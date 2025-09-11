from flask import Flask, jsonify, render_template, request, send_file
import cv2
import numpy as np
import base64
import datetime
import geocoder
from insightface.app import FaceAnalysis
from math import radians, sin, cos, acos
import qrcode
import io
from urllib.parse import urlencode
import os

app = Flask(__name__)
# Merchant config (destination for all UPI payments)
MERCHANT_UPI_ID = os.getenv('MERCHANT_UPI_ID', 'yourvpa@upi')
MERCHANT_NAME = os.getenv('MERCHANT_NAME', 'AI Bus')


# Initialize InsightFace model
face_app = FaceAnalysis(name='buffalo_l', providers=['CPUExecutionProvider'])
face_app.prepare(ctx_id=0, det_size=(640, 640))

# Global data
captured_faces = []
captured_embeddings = []
captured_images = []
locations = []
timestamps = []
latlngs = []

def enhance_image(img):
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
    cl = clahe.apply(l)
    merged = cv2.merge((cl, a, b))
    enhanced_img = cv2.cvtColor(merged, cv2.COLOR_LAB2BGR)
    return enhanced_img

def capture_face():
    cap = cv2.VideoCapture(0)
    if not cap.isOpened():
        return None, None
    ret, frame = cap.read()
    cap.release()
    if not ret:
        return None, None

    frame = enhance_image(frame)
    faces = face_app.get(frame)
    if not faces:
        return None, None

    face = max(faces, key=lambda f: f.det_score)
    x1, y1, x2, y2 = face.bbox.astype(int)
    face_img = frame[y1:y2, x1:x2]
    embedding = face.normed_embedding
    return face_img, embedding

def compare_faces(emb1, emb2):
    emb1 = np.array(emb1)
    emb2 = np.array(emb2)
    sim = np.dot(emb1, emb2) / (np.linalg.norm(emb1) * np.linalg.norm(emb2))
    return sim > 0.6

def get_location():
    g = geocoder.ip('me')
    if g.ok:
        return g.address, g.latlng
    return "Unknown Location", [0.0, 0.0]

@app.route('/reset_face_detection', methods=['POST'])
def reset_face_detection():
    global captured_faces, captured_embeddings, captured_images, locations, timestamps, latlngs
    captured_faces.clear()
    captured_embeddings.clear()
    captured_images.clear()
    locations.clear()
    timestamps.clear()
    latlngs.clear()
    return jsonify({"message": "Face detection reset successful."})

@app.route('/start_face_detection', methods=['POST', 'GET'])
def start_face_detection():
    global captured_faces, captured_embeddings, captured_images, locations, timestamps, latlngs

    if request.method == 'POST':
        data = request.get_json()
        latitude = data.get('latitude')
        longitude = data.get('longitude')
    else:
        latitude = None
        longitude = None

    if len(captured_faces) < 2:
        face_img, embedding = capture_face()

        if face_img is None:
            return jsonify({"error": "Unable to detect face."}), 400

        _, buffer = cv2.imencode('.jpg', face_img)
        captured_image = base64.b64encode(buffer).decode('utf-8')

        captured_faces.append(face_img)
        captured_embeddings.append(embedding)
        captured_images.append(captured_image)

        if latitude is not None and longitude is not None:
            location = f"Lat: {latitude}, Lng: {longitude}"
            latlng = [latitude, longitude]
        else:
            location, latlng = get_location()
        locations.append(location)
        latlngs.append(latlng)

        timestamp = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        timestamps.append(timestamp)

        if len(captured_faces) < 2:
            return jsonify({
                "image": captured_image,
                "location": location,
                "latlng": latlng,
                "timestamp": timestamp,
                "result": "Face detected. Click again to compare.",
                "hide_button": False
            })
        else:
            is_same = compare_faces(captured_embeddings[0], captured_embeddings[1])
            result = "Faces match!" if is_same else "Faces do not match!"
            # Calculate distance using Spherical Law of Cosines with clamping
            lat1, lon1 = map(radians, map(float, latlngs[0]))
            lat2, lon2 = map(radians, map(float, latlngs[1]))
            R = 6371000  # meters
            arg = sin(lat1)*sin(lat2) + cos(lat1)*cos(lat2)*cos(lon2-lon1)
            arg = max(-1.0, min(1.0, arg))
            distance = acos(arg) * R
            return jsonify({
                "image1": captured_images[0],
                "location1": locations[0],
                "latlng1": latlngs[0],
                "timestamp1": timestamps[0],
                "image2": captured_images[1],
                "location2": locations[1],
                "latlng2": latlngs[1],
                "timestamp2": timestamps[1],
                "result": result,
                "distance_m": distance,
                "hide_button": True
            })
    else:
        return jsonify({"error": "Faces already captured."}), 400

@app.route('/api/calc_distance')
def api_calc_distance():
    try:
        lat1 = float(request.args.get('lat1'))
        lng1 = float(request.args.get('lng1'))
        lat2 = float(request.args.get('lat2'))
        lng2 = float(request.args.get('lng2'))
        R = 6371000  # meters
        lat1r, lng1r, lat2r, lng2r = map(radians, [lat1, lng1, lat2, lng2])
        arg = sin(lat1r)*sin(lat2r) + cos(lat1r)*cos(lat2r)*cos(lng2r-lng1r)
        arg = max(-1.0, min(1.0, arg))
        distance = acos(arg) * R
        return jsonify({'distance_m': distance})
    except Exception as e:
        return jsonify({'error': str(e)}), 400

@app.route('/api/merchant')
def api_merchant():
    return jsonify({'pa': MERCHANT_UPI_ID, 'pn': MERCHANT_NAME})

@app.route('/api/upi_link')
def api_upi_link():
    am = request.args.get('am', '0.00')
    tn = request.args.get('tn', 'AI Bus Fare')
    try:
        am_val = max(0.0, float(am))
    except Exception:
        am_val = 0.0
    tr = request.args.get('tr') or datetime.datetime.utcnow().strftime('TXN%Y%m%d%H%M%S%f')
    params = {
        'pa': MERCHANT_UPI_ID,
        'pn': MERCHANT_NAME,
        'am': f'{am_val:.2f}',
        'cu': 'INR',
        'tn': tn,
        'tr': tr
    }
    link = 'upi://pay?' + urlencode(params)
    return jsonify({'link': link, 'tr': tr})

@app.route('/api/upi_qr')
def api_upi_qr():
    am = request.args.get('am', '0.00')
    tn = request.args.get('tn', 'AI Bus Fare')
    try:
        am_val = max(0.0, float(am))
    except Exception:
        am_val = 0.0
    tr = request.args.get('tr') or datetime.datetime.utcnow().strftime('TXN%Y%m%d%H%M%S%f')
    params = {
        'pa': MERCHANT_UPI_ID,
        'pn': MERCHANT_NAME,
        'am': f'{am_val:.2f}',
        'cu': 'INR',
        'tn': tn,
        'tr': tr
    }
    upi_uri = 'upi://pay?' + urlencode(params)
    img = qrcode.make(upi_uri)
    buf = io.BytesIO()
    img.save(buf, format='PNG')
    buf.seek(0)
    return send_file(buf, mimetype='image/png')

# Routes to render templates
@app.route('/')
def home():
    return render_template('Home.html')

@app.route('/l')
def login():
    return render_template('Login.html')

@app.route('/s')
def signup():
    return render_template('Signup.html')

@app.route('/dm')
def dm():
    return render_template('Distance_map.html')

@app.route('/fc')
def fc():
    return render_template('Fare_Calculation.html')

@app.route('/fd')
def fd():
    return render_template('Face_Detection.html')

@app.route('/p')
def p():
    return render_template('Payment.html')

if __name__ == '__main__':
    app.run(debug=True)
