import uuid
import logging
from fastapi import APIRouter, Depends, HTTPException
from firebase_admin import firestore

from app.api.deps import get_current_user_id
from app.core.security import get_firestore_db
from app.models.schemas import SessionStartRequest, SessionStartResponse

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/session", tags=["session"])


@router.post("/start", response_model=SessionStartResponse)
async def start_session(
    payload: SessionStartRequest,
    user_id: str = Depends(get_current_user_id),
) -> SessionStartResponse:
    """Mulai sesi baru untuk user (1 Akun = 1 Perangkat).

    Menghasilkan sessionId baru dan menyimpannya di Firestore melalui Firebase Admin SDK.
    Perangkat lama yang memantau dokumen sesi akan mendeteksi perubahan activeSessionId
    dan melakukan logout paksa dengan menampilkan deviceName baru.
    """
    if not payload.device_name or not payload.device_name.strip():
        device_name = "Perangkat Tidak Dikenal"
    else:
        device_name = payload.device_name.strip()

    session_id = str(uuid.uuid4())
    db = get_firestore_db()

    if db is not None:
        try:
            # 1. Update subkoleksi session (users/{uid}/session/active)
            session_ref = db.collection("users").document(user_id).collection("session").document("active")
            session_data = {
                "activeSessionId": session_id,
                "deviceName": device_name,
                "updatedAt": firestore.SERVER_TIMESTAMP,
            }
            session_ref.set(session_data, merge=True)

            # 2. Update dokumen user (users/{uid}) untuk kemudahan query backend
            user_ref = db.collection("users").document(user_id)
            user_ref.set({
                "activeSessionId": session_id,
                "deviceName": device_name,
                "lastActiveAt": firestore.SERVER_TIMESTAMP,
            }, merge=True)

            logger.info("Session started for user %s on device %s (session %s)", user_id, device_name, session_id)
        except Exception as e:
            logger.error("Failed to persist session to Firestore: %s", e)
            raise HTTPException(
                status_code=500,
                detail=f"Failed to record session in Firestore: {e}"
            )
    else:
        logger.warning(
            "Firestore client unavailable while starting session for user %s on %s. "
            "Place serviceAccountKey.json in songfy_be to enable Firestore Admin session writes.",
            user_id,
            device_name,
        )
        return SessionStartResponse(
            session_id=session_id,
            device_name=device_name,
            user_id=user_id,
            message="Session created (Firestore Admin unconfigured in BE)",
        )

    return SessionStartResponse(
        session_id=session_id,
        device_name=device_name,
        user_id=user_id,
        message="Session started successfully",
    )


@router.get("/active")
async def get_active_session(
    user_id: str = Depends(get_current_user_id),
):
    """Ambil informasi sesi perangkat yang sedang aktif di Firestore."""
    db = get_firestore_db()
    if db is None:
        return {"device_name": "Tidak diketahui", "active_session_id": None, "updated_at": None}

    doc = db.collection("users").document(user_id).collection("session").document("active").get()
    if not doc.exists:
        return {"device_name": None, "active_session_id": None, "updated_at": None}

    data = doc.to_dict() or {}
    updated_at = data.get("updatedAt")
    return {
        "device_name": data.get("deviceName", "Tidak diketahui"),
        "active_session_id": data.get("activeSessionId"),
        "updated_at": str(updated_at) if updated_at else None,
    }


@router.post("/kick-others", response_model=SessionStartResponse)
async def kick_other_sessions(
    payload: SessionStartRequest,
    user_id: str = Depends(get_current_user_id),
) -> SessionStartResponse:
    """Paksa logout semua perangkat lain dengan mendaftarkan sesi baru untuk perangkat ini."""
    return await start_session(payload, user_id)

