from fastapi import Header, HTTPException
from firebase_admin import auth as firebase_auth
from slowapi import Limiter
from slowapi.util import get_remote_address

# Shared global limiter instance across all routes and app.state
limiter = Limiter(key_func=get_remote_address)


def get_current_user_id(authorization: str = Header(...)) -> str:
    """Verifikasi Firebase ID token yang dikirim dari Android app."""
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid authorization header")

    token = authorization.split("Bearer ")[1]

    try:
        decoded = firebase_auth.verify_id_token(token)
        return decoded["uid"]
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
