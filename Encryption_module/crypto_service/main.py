import os
import base64
import hashlib
import argparse
import logging
from datetime import datetime

import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from cryptography.hazmat.primitives.asymmetric import rsa, padding
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

# --- Configuration & Logging ---
logging.basicConfig(
    level=logging.INFO,
    format='[%(asctime)s] [%(levelname)s] %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger("crypto_service")

app = FastAPI(title="Python crypto file security transfer service")

# Globals to be set by argument parser
KEY_DIR = "./crypto_keys"
PRIVATE_KEY_FILE = ""
PUBLIC_KEY_FILE = ""

PRIVATE_KEY_MODE = 0o600
PUBLIC_KEY_MODE = 0o644

# --- Helper Functions ---
def _write_key_file(path: str, data: bytes, mode: int):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    if os.name == "nt":
        with open(path, "wb") as f:
            f.write(data)
    else:
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, mode)
        with os.fdopen(fd, "wb") as f:
            f.write(data)
    _protect_key_file(path, mode)

def _protect_key_file(path: str, mode: int):
    if not path or not os.path.exists(path):
        return
    try:
        os.chmod(path, mode)
    except OSError as exc:
        logger.warning(f"unable to chmod key file path={path} error=\"{exc}\"")

def protect_existing_key_files():
    _protect_key_file(PRIVATE_KEY_FILE, PRIVATE_KEY_MODE)
    _protect_key_file(PUBLIC_KEY_FILE, PUBLIC_KEY_MODE)

def normalize_private_key_input(private_key_text: str) -> bytes:
    if private_key_text is None or not private_key_text.strip():
        raise ValueError("privateKey is required")
    normalized = (
        private_key_text.strip()
        .replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
    )
    if "-----BEGIN" in normalized and "PRIVATE KEY-----" in normalized:
        return (normalized.rstrip() + "\n").encode("utf-8")
    try:
        decoded = base64.b64decode(normalized, validate=True)
    except Exception as exc:
        raise ValueError("privateKey must be PEM text or Base64-encoded PEM") from exc
    decoded_text = decoded.decode("utf-8")
    if "-----BEGIN" not in decoded_text or "PRIVATE KEY-----" not in decoded_text:
        raise ValueError("decoded privateKey is not a PEM private key")
    return (decoded_text.rstrip() + "\n").encode("utf-8")

def load_private_key() -> rsa.RSAPrivateKey:
    if not os.path.exists(PRIVATE_KEY_FILE):
        raise RuntimeError("Private key not found")
    with open(PRIVATE_KEY_FILE, "rb") as f:
        return serialization.load_pem_private_key(f.read(), password=None)

def load_public_key(pem_data: bytes) -> rsa.RSAPublicKey:
    return serialization.load_pem_public_key(pem_data)

def generate_and_persist_keypair():
    logger.info(f"generating RSA key pair key_dir={KEY_DIR}")
    private_key = rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048
    )
    
    private_pem = private_key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.TraditionalOpenSSL,
        encryption_algorithm=serialization.NoEncryption()
    )
    
    public_pem = private_key.public_key().public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )
    
    os.makedirs(KEY_DIR, exist_ok=True)
    _write_key_file(PRIVATE_KEY_FILE, private_pem, PRIVATE_KEY_MODE)
    _write_key_file(PUBLIC_KEY_FILE, public_pem, PUBLIC_KEY_MODE)
        
    logger.info(f"generated RSA key pair private_key_file={PRIVATE_KEY_FILE} public_key_file={PUBLIC_KEY_FILE}")

def import_private_key_and_persist(private_pem: bytes) -> bytes:
    key = serialization.load_pem_private_key(private_pem, password=None)
    norm_priv = key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.TraditionalOpenSSL,
        encryption_algorithm=serialization.NoEncryption()
    )
    pub_pem = key.public_key().public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )
    
    os.makedirs(KEY_DIR, exist_ok=True)
    _write_key_file(PRIVATE_KEY_FILE, norm_priv, PRIVATE_KEY_MODE)
    _write_key_file(PUBLIC_KEY_FILE, pub_pem, PUBLIC_KEY_MODE)
    
    return pub_pem

# --- RSA Padding Rules mapped from C++ (OpenSSL params) ---
# C++ uses: RSA_PKCS1_PSS_PADDING and RSA_PSS_SALTLEN_DIGEST (-1)
PSS_PADDING = padding.PSS(
    mgf=padding.MGF1(hashes.SHA256()),
    salt_length=padding.PSS.DIGEST_LENGTH
)
# C++ uses: RSA_PKCS1_OAEP_PADDING, MGF1-SHA256, Hash-SHA256
OAEP_PADDING = padding.OAEP(
    mgf=padding.MGF1(algorithm=hashes.SHA256()),
    algorithm=hashes.SHA256(),
    label=None
)

# --- Exception Handler ---
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"request failed error=\"{str(exc)}\"")
    return JSONResponse(status_code=500, content={"error": str(exc)})

# --- Pydantic Models for JSON Requests ---
class SignRequest(BaseModel):
    data: str

class VerifyRequest(BaseModel):
    publicKey: str
    data: str
    signature: str

class RsaEncryptRequest(BaseModel):
    publicKey: str
    plain: str

class RsaDecryptRequest(BaseModel):
    cipher: str

class AesGcmEncryptRequest(BaseModel):
    key: str
    plain: str

class AesGcmDecryptRequest(BaseModel):
    key: str
    nonce: str
    ciphertext: str
    tag: str

class KeyDataRequest(BaseModel):
    publicKey: str = None
    privateKey: str = None

class ImportFileRequest(BaseModel):
    path: str

# --- API Routes ---

@app.get("/health")
def health():
    return {"status": "ok", "keyDir": KEY_DIR}

@app.get("/key/public")
def get_public_key():
    with open(PUBLIC_KEY_FILE, "r") as f:
        return {"publicKey": f.read()}

@app.get("/key/private")
def get_private_key():
    with open(PRIVATE_KEY_FILE, "r") as f:
        return {"privateKey": f.read()}

@app.get("/key/status")
def get_key_status():
    return {
        "hasPrivateKey": "true" if os.path.exists(PRIVATE_KEY_FILE) else "false",
        "hasPublicKey": "true" if os.path.exists(PUBLIC_KEY_FILE) else "false"
    }

@app.post("/key/generate")
def generate_key():
    if os.path.exists(PRIVATE_KEY_FILE) or os.path.exists(PUBLIC_KEY_FILE):
        logger.warning("key generation rejected because key pair already exists")
        return JSONResponse(
            status_code=409, 
            content={"success": "false", "error": "Key pair already exists"}
        )
        
    generate_and_persist_keypair()
    with open(PRIVATE_KEY_FILE, "r") as fp, open(PUBLIC_KEY_FILE, "r") as pub:
        return {
            "success": "true",
            "privateKey": fp.read(),
            "publicKey": pub.read()
        }

@app.post("/key/delete")
def delete_key():
    del_priv = False
    del_pub = False
    if os.path.exists(PRIVATE_KEY_FILE):
        os.remove(PRIVATE_KEY_FILE)
        del_priv = True
    if os.path.exists(PUBLIC_KEY_FILE):
        os.remove(PUBLIC_KEY_FILE)
        del_pub = True
        
    return {
        "success": "true",
        "deletedPrivateKey": "true" if del_priv else "false",
        "deletedPublicKey": "true" if del_pub else "false"
    }

@app.post("/sign")
def sign_data(req: SignRequest):
    private_key = load_private_key()
    signature = private_key.sign(req.data.encode('utf-8'), PSS_PADDING, hashes.SHA256())
    return {"signature": base64.b64encode(signature).decode('ascii')}

@app.post("/verify")
def verify_data(req: VerifyRequest):
    public_key = load_public_key(req.publicKey.encode('utf-8'))
    signature_bytes = base64.b64decode(req.signature)
    try:
        public_key.verify(signature_bytes, req.data.encode('utf-8'), PSS_PADDING, hashes.SHA256())
        return {"valid": "true"}
    except Exception:
        return {"valid": "false"}

@app.post("/aes/generate")
def generate_aes():
    return {"key": base64.b64encode(os.urandom(32)).decode('ascii')}

@app.post("/rsa/encrypt")
def rsa_encrypt(req: RsaEncryptRequest):
    public_key = load_public_key(req.publicKey.encode('utf-8'))
    plain_bytes = base64.b64decode(req.plain)
    cipher_bytes = public_key.encrypt(plain_bytes, OAEP_PADDING)
    return {"cipher": base64.b64encode(cipher_bytes).decode('ascii')}

@app.post("/rsa/decrypt")
def rsa_decrypt(req: RsaDecryptRequest):
    private_key = load_private_key()
    cipher_bytes = base64.b64decode(req.cipher)
    plain_bytes = private_key.decrypt(cipher_bytes, OAEP_PADDING)
    return {"plain": base64.b64encode(plain_bytes).decode('ascii')}

@app.post("/aes-gcm/encrypt")
def aes_gcm_encrypt(req: AesGcmEncryptRequest):
    key = base64.b64decode(req.key)
    plain = base64.b64decode(req.plain)
    if len(key) != 32:
        raise ValueError("AES-256 key must be 32 bytes")

    nonce = os.urandom(12)
    aesgcm = AESGCM(key)
    # cryptography library returns ciphertext + tag appended together
    encrypted_data = aesgcm.encrypt(nonce, plain, None)
    ciphertext = encrypted_data[:-16]
    tag = encrypted_data[-16:]
    
    return {
        "nonce": base64.b64encode(nonce).decode('ascii'),
        "ciphertext": base64.b64encode(ciphertext).decode('ascii'),
        "tag": base64.b64encode(tag).decode('ascii')
    }

@app.post("/aes-gcm/decrypt")
def aes_gcm_decrypt(req: AesGcmDecryptRequest):
    key = base64.b64decode(req.key)
    nonce = base64.b64decode(req.nonce)
    ciphertext = base64.b64decode(req.ciphertext)
    tag = base64.b64decode(req.tag)
    
    if len(key) != 32:
        raise ValueError("AES-256 key must be 32 bytes")
    if len(tag) != 16:
        raise ValueError("AES-GCM tag must be 16 bytes")

    aesgcm = AESGCM(key)
    plain = aesgcm.decrypt(nonce, ciphertext + tag, None)
    return {"plain": base64.b64encode(plain).decode('ascii')}

@app.post("/key/fingerprint")
def get_fingerprint(req: KeyDataRequest):
    h = hashlib.sha256()
    h.update(req.publicKey.encode('utf-8'))
    return {"fingerprint": h.hexdigest()}

@app.post("/key/derive-public")
def derive_public(req: KeyDataRequest):
    key = serialization.load_pem_private_key(normalize_private_key_input(req.privateKey), password=None)
    pub_pem = key.public_key().public_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PublicFormat.SubjectPublicKeyInfo
    )
    return {"publicKey": pub_pem.decode('utf-8')}

@app.post("/key/import-text")
def import_text(req: KeyDataRequest):
    pub_pem = import_private_key_and_persist(normalize_private_key_input(req.privateKey))
    return {
        "success": "true",
        "publicKey": pub_pem.decode('utf-8')
    }

@app.post("/key/import-file")
def import_file(req: ImportFileRequest):
    with open(req.path, "rb") as f:
        private_pem = f.read()
    pub_pem = import_private_key_and_persist(private_pem)
    return {
        "success": "true",
        "publicKey": pub_pem.decode('utf-8')
    }

# --- Entry Point ---
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Python crypto file security transfer service")
    parser.add_argument("--host", default="0.0.0.0", help="Listen host, default 0.0.0.0")
    parser.add_argument("--port", type=int, default=20202, help="Listen port, default 20202")
    parser.add_argument("--key-dir", dest="key_dir", default="./crypto_keys", help="Key storage directory, default ./crypto_keys")
    args = parser.parse_args()

    # Apply configuration globals
    KEY_DIR = args.key_dir
    PRIVATE_KEY_FILE = os.path.join(KEY_DIR, "private_key.pem")
    PUBLIC_KEY_FILE = os.path.join(KEY_DIR, "public_key.pem")
    
    os.makedirs(KEY_DIR, exist_ok=True)
    protect_existing_key_files()
    logger.info(f"key directory configured key_dir={KEY_DIR} private_key_file={PRIVATE_KEY_FILE} public_key_file={PUBLIC_KEY_FILE}")
    logger.info(f"crypto service starting url=http://{args.host}:{args.port}")

    # Run Uvicorn server dynamically
    uvicorn.run(app, host=args.host, port=args.port, log_config=None)
