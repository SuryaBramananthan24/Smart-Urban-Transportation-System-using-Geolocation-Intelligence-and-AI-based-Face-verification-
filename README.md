# Smart Urban Transportation System using Geolocation Intelligence and AI based Face verification:
This project is an AI-powered bus management system that automates fare collection and passenger verification. It uses facial recognition to identify passengers, IP geolocation to calculate travel distance, and charges fares automatically through a Flask integrated with the HERE Maps API improving efficiency and reducing fare evasion.
## Features  
-  **Face Verification** – Passenger identity captured at boarding and exit using **InsightFace** + **OpenCV (CLAHE preprocessing)**.  
-  **Distance-Based Pricing** – Pickup and drop-off points detected via **IP geolocation**, distance calculated using the **Spherical Law of Cosines formula** with **HERE Maps API**.  
-  **Fast & Reliable** – Average processing time ~3.2s per passenger.  
-  **Cost-Effective** – Reduces fare evasion, removes conductor costs, projected **ROI: 180% by 2029**.  
---

## System Architecture  
- **Frontend**: Responsive web interface (tablet/PC-friendly) for passenger interaction.  
- **Backend**: Flask server for face recognition, fare computation, and integration.  
- **APIs**: HERE Maps API for geolocation & distance calculation.
---
## Tech Stack  
- **Python**: Flask, Geocoder, OpenCV, InsightFace  
- **Database**: MySQL  
- **Mapping API**: HERE Maps API  
- **Frontend**: HTML, CSS, JavaScript  
---
## Installation & Setup  

### 1. Clone the Repository  
```bash
git clone https://github.com/your-username/Smart-Urban-Transportation-System-using-Geolocation-Intelligence-and-AI-based-Face-verification.git
cd Smart-Urban-Transportation-System-using-Geolocation-Intelligence-and-AI-based-Face-verification
```
### 2.Create a Virtual Environment
```bash
python -m venv venv
source venv/bin/activate   # On Linux/Mac
venv\Scripts\activate      # On Windows
```
### 3.Install Dependencies
```bash
pip install -r requirements.txt
```
### 4.Setup Environment Variables
```bash
HERE_API_KEY=your_here_maps_api_key
DATABASE_URL=mysql://username:password@localhost/yourdbname
```
### 5.Run the Application
```bash
python app.py
```
