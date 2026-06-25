-- ============================================
-- MediFind Database Schema
-- Run this in MySQL Workbench or CLI first
-- ============================================

CREATE DATABASE IF NOT EXISTS medifind;
USE medifind;

-- USERS TABLE (customers + admin)
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(15),
    shop_name VARCHAR(150),
    shop_address VARCHAR(255),
    pincode VARCHAR(10),
    license_number VARCHAR(80),
    license_photo LONGTEXT,
    approval_status ENUM('NOT_REQUIRED','PENDING','APPROVED','REJECTED') DEFAULT 'NOT_REQUIRED',
    role ENUM('CUSTOMER', 'ADMIN', 'SHOPKEEPER') DEFAULT 'CUSTOMER',
    saved_lat DOUBLE,
    saved_lng DOUBLE,
    saved_address VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- MEDICAL SHOPS TABLE
CREATE TABLE shops (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    owner_name VARCHAR(100),
    phone VARCHAR(15) NOT NULL,
    address VARCHAR(255) NOT NULL,
    lat DOUBLE NOT NULL,
    lng DOUBLE NOT NULL,
    is_open BOOLEAN DEFAULT FALSE,
    is_24hr BOOLEAN DEFAULT FALSE,
    open_time TIME,
    close_time TIME,
    license_number VARCHAR(50),
    pincode VARCHAR(10),
    views BIGINT DEFAULT 0,
    created_by BIGINT,  -- admin user id
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id)
);

-- DAILY SHOPKEEPER BILLS
CREATE TABLE sale_bills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    shop_name VARCHAR(150) NOT NULL,
    pincode VARCHAR(10),
    customer_name VARCHAR(100),
    medicine_name VARCHAR(150) NOT NULL,
    quantity INT DEFAULT 1,
    price DECIMAL(12,2) NOT NULL,
    sgst DECIMAL(12,2) DEFAULT 0,
    cgst DECIMAL(12,2) DEFAULT 0,
    service_tax DECIMAL(12,2) DEFAULT 0,
    total DECIMAL(12,2) NOT NULL,
    billed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
);

-- INVENTORY TABLE (medicine stock per shop)
CREATE TABLE inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    medicine_name VARCHAR(150) NOT NULL,
    quantity INT DEFAULT 0,
    unit VARCHAR(20) DEFAULT 'strips',
    price DECIMAL(10,2),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
);

-- SHORTAGE LIST TABLE (replaces shopkeeper's notebook)
CREATE TABLE shortage_list (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    medicine_name VARCHAR(150) NOT NULL,
    customer_name VARCHAR(100),        -- NULL if general shortage
    customer_phone VARCHAR(15),
    customer_id BIGINT,                -- NULL if offline request
    quantity_needed INT DEFAULT 1,
    advance_paid DECIMAL(10,2) DEFAULT 0,
    payment_status ENUM('NONE', 'PENDING', 'PAID', 'REFUNDED') DEFAULT 'NONE',
    payment_transaction_id VARCHAR(100),
    status ENUM('PENDING', 'ORDERED', 'ARRIVED', 'DELIVERED', 'CANCELLED') DEFAULT 'PENDING',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE SET NULL
);

-- CUSTOMER QUERIES TABLE (messages to shop)
CREATE TABLE customer_queries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    customer_id BIGINT,
    customer_name VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(15) NOT NULL,
    medicine_name VARCHAR(150) NOT NULL,
    message TEXT,
    status ENUM('OPEN', 'CONTACTED', 'RESOLVED') DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    FOREIGN KEY (customer_id) REFERENCES users(id) ON DELETE SET NULL
);

-- SHOP VIEWS ANALYTICS TABLE
CREATE TABLE shop_views (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    viewer_ip VARCHAR(45),
    viewed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
);

-- SAMPLE ADMIN USER. The backend startup initializer resets this account to admin123.
INSERT INTO users (name, email, password, role) VALUES
('Admin', 'admin@medifind.com', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.', 'ADMIN');
