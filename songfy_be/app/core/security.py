"""
Firebase Admin SDK initialization.
Called once at app startup via main.py.
Uses FIREBASE_PROJECT_ID from .env / env vars.
"""
import logging
import os
import firebase_admin
from firebase_admin import credentials, firestore
from app.core.config import get_settings

logger = logging.getLogger(__name__)


def initialize_firebase() -> None:
    """Initialize Firebase Admin SDK if not already initialized."""
    if firebase_admin._apps:
        return  # Already initialized — skip

    try:
        settings = get_settings()

        # Check candidate locations for service account key
        candidates = [
            settings.FIREBASE_CREDENTIALS_PATH,
            os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", ""),
            os.path.join(os.getcwd(), "serviceAccountKey.json"),
            os.path.join(os.path.dirname(__file__), "..", "..", "serviceAccountKey.json"),
            os.path.join(os.getcwd(), "credentials", "serviceAccountKey.json"),
        ]

        sa_path = next((c for c in candidates if c and os.path.isfile(c)), None)

        if sa_path:
            logger.info("Initializing Firebase Admin with service account key: %s", sa_path)
            cred = credentials.Certificate(sa_path)
            firebase_admin.initialize_app(cred, {
                "projectId": settings.FIREBASE_PROJECT_ID or None,
            })
        elif settings.FIREBASE_PROJECT_ID:
            logger.info("Initializing Firebase Admin with projectId: %s", settings.FIREBASE_PROJECT_ID)
            firebase_admin.initialize_app(options={
                "projectId": settings.FIREBASE_PROJECT_ID,
            })
        else:
            firebase_admin.initialize_app()
    except Exception as e:
        logger.warning("Firebase init skipped: %s", e)


def get_firestore_db():
    """Retrieve Firestore client or None if credentials not configured."""
    try:
        return firestore.client()
    except Exception as e:
        logger.warning("Firestore client unavailable: %s", e)
        return None