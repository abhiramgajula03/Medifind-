# MediFind — Complete Launch Guide
# ===================================
# Follow these steps EXACTLY in order

## TOOLS YOU NEED (install if not already installed)
# - Java 17+          → https://adoptium.net
# - Maven             → https://maven.apache.org/download.cgi
# - Node.js 18+       → https://nodejs.org
# - MySQL 8.0+        → https://dev.mysql.com/downloads/mysql/
# - IntelliJ IDEA     → https://www.jetbrains.com/idea/ (Community is free)
# - VS Code           → https://code.visualstudio.com (for React)

## ─────────────────────────────────────────
## STEP 1: Set up the MySQL Database
## ─────────────────────────────────────────
# Open MySQL Workbench or MySQL CLI and run:

mysql -u root -p
# Enter your MySQL root password

# Then paste and run the contents of:
#   medifind/database/schema.sql

# This creates the database and all tables.

## ─────────────────────────────────────────
## STEP 2: Configure Backend
## ─────────────────────────────────────────
# Open: medifind/backend/src/main/resources/application.properties
# Change these two lines to your MySQL password:

spring.datasource.username=root
spring.datasource.password=YOUR_ACTUAL_MYSQL_PASSWORD

# Leave everything else as-is for now.

## ─────────────────────────────────────────
## STEP 3: Get a Google Maps API Key (FREE)
## ─────────────────────────────────────────
# 1. Go to: https://console.cloud.google.com
# 2. Create a new project called "MediFind"
# 3. Go to APIs & Services → Enable APIs:
#      - Maps JavaScript API
#      - Geocoding API
#      - Directions API
# 4. Go to APIs & Services → Credentials → Create API Key
# 5. Copy the key

# Put the key in TWO places:
#   backend/src/main/resources/application.properties:
#     google.maps.api.key=YOUR_KEY_HERE
#
#   frontend/.env:
#     REACT_APP_GOOGLE_MAPS_KEY=YOUR_KEY_HERE

## ─────────────────────────────────────────
## STEP 4: Run the Spring Boot Backend
## ─────────────────────────────────────────

# Option A: Using IntelliJ IDEA (recommended)
# 1. Open IntelliJ → File → Open → select medifind/backend folder
# 2. Wait for Maven to download dependencies (first time takes ~2 min)
# 3. Open MediFindApplication.java
# 4. Click the green ▶ Run button
# 5. You should see: "Started MediFindApplication on port 8080"

# Option B: Using terminal/command prompt
cd medifind/backend
mvn spring-boot:run

# ✅ Backend is running at: http://localhost:8080
# Test it: open browser → http://localhost:8080/api/shops/nearby?lat=17.385&lng=78.486
# You should see: [] (empty array — no shops yet, which is correct)

## ─────────────────────────────────────────
## STEP 5: Run the React Frontend
## ─────────────────────────────────────────

# Open a NEW terminal window (backend must stay running)
cd medifind/frontend

# Install dependencies (first time only, takes ~1 min)
npm install

# Start the React app
npm start

# Browser will open automatically at: http://localhost:3000
# ✅ You should see the MediFind map page

## ─────────────────────────────────────────
## STEP 6: Add Your First Shop (Admin)
## ─────────────────────────────────────────
# The admin user is already in the database:
#   Email:    admin@medifind.com
#   Password: admin123

# Login via: http://localhost:3000/login

# Or use Postman / browser to call the API directly:
# POST http://localhost:8080/api/auth/login
# Body (JSON):
# {
#   "email": "admin@medifind.com",
#   "password": "admin123"
# }
# Copy the "token" from the response.

# Then add a shop:
# POST http://localhost:8080/api/shops
# Header: Authorization: Bearer YOUR_TOKEN
# Body:
# {
#   "name": "Sri Sai Medical Store",
#   "phone": "9876543210",
#   "address": "Ameerpet, Hyderabad",
#   "lat": 17.4375,
#   "lng": 78.4482,
#   "isOpen": true,
#   "is24hr": false,
#   "openTime": "08:00",
#   "closeTime": "22:00"
# }

## ─────────────────────────────────────────
## STEP 7: Test Nearby Search
## ─────────────────────────────────────────
# Go to http://localhost:3000
# Click "Find Shops Near Me"
# Allow location access
# You should see the shop appear on the map if you're within 4km

## ─────────────────────────────────────────
## STEP 8: Push to GitHub
## ─────────────────────────────────────────

# 1. Create a new repository on github.com (name: medifind)
# 2. Create a .gitignore file in medifind/ root:

cat > medifind/.gitignore << 'EOF'
# Backend
backend/target/
*.class
*.jar

# Frontend
frontend/node_modules/
frontend/build/

# Environment (NEVER commit these)
frontend/.env
backend/src/main/resources/application.properties

# IDE
.idea/
*.iml
.vscode/
EOF

# 3. Initialize and push:
cd medifind
git init
git add .
git commit -m "Initial commit: MediFind medical shop finder"
git remote add origin https://github.com/YOUR_USERNAME/medifind.git
git push -u origin main

## ─────────────────────────────────────────
## SUMMARY: What's running where
## ─────────────────────────────────────────
# Spring Boot Backend  → http://localhost:8080
# React Frontend       → http://localhost:3000
# MySQL Database       → localhost:3306/medifind

## KEY API ENDPOINTS (test with Postman)
# POST /api/auth/register        → Customer registration
# POST /api/auth/login           → Login (get JWT token)
# GET  /api/shops/nearby?lat=&lng=&filter=open   → Find nearby shops
# GET  /api/shops/{id}           → Shop details
# POST /api/shops                → Add shop (admin only)
# PATCH /api/shops/{id}/status   → Toggle open/close (shopkeeper)
# GET  /api/shortage/shop/{id}   → Shortage list (shopkeeper)
# POST /api/shortage/shop/{id}/offline → Add walk-in shortage entry
# GET  /api/shortage/shop/{id}/export  → Download CSV for distributor
