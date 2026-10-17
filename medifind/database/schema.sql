-- ============================================
-- MediFind Database Schema
-- ============================================

CREATE DATABASE IF NOT EXISTS medifind;
USE medifind;

-- Users
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    role ENUM('CUSTOMER','SHOPKEEPER','ADMIN') DEFAULT 'CUSTOMER',
    approval_status ENUM('PENDING','APPROVED','REJECTED','NOT_REQUIRED') DEFAULT 'PENDING',
    shop_name VARCHAR(150),
    shop_address TEXT,
    pincode VARCHAR(10),
    license_number VARCHAR(120),
    license_photo LONGTEXT,
    saved_address TEXT,
    saved_lat DOUBLE,
    saved_lng DOUBLE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Shops
CREATE TABLE IF NOT EXISTS shops (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    owner_name VARCHAR(120),
    phone VARCHAR(20),
    address TEXT,
    pincode VARCHAR(10),
    license_number VARCHAR(120),
    license_photo LONGTEXT,
    lat DOUBLE,
    lng DOUBLE,
    is_open BOOLEAN DEFAULT TRUE,
    is_24hr BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Shortage list
CREATE TABLE IF NOT EXISTS shortage_list (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    medicine_name VARCHAR(150) NOT NULL,
    customer_name VARCHAR(120),
    customer_phone VARCHAR(20),
    customer_id BIGINT,
    quantity_needed INT DEFAULT 1,
    advance_paid DECIMAL(10,2) DEFAULT 0,
    payment_status ENUM('NONE','PENDING','PAID','REFUNDED') DEFAULT 'NONE',
    payment_transaction_id VARCHAR(120),
    status ENUM('PENDING','ORDERED','ARRIVED','DELIVERED','CANCELLED') DEFAULT 'PENDING',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Sale bills
CREATE TABLE IF NOT EXISTS sale_bills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    shop_name VARCHAR(150) NOT NULL,
    pincode VARCHAR(10),
    customer_name VARCHAR(120),
    medicine_name VARCHAR(150) NOT NULL,
    quantity INT DEFAULT 1,
    price DECIMAL(12,2) NOT NULL,
    inventory_id BIGINT,
    selling_unit VARCHAR(50),
    pack_size INT,
    pack VARCHAR(100),
    batch_no VARCHAR(100),
    expiry_date DATE,
    manufacturer VARCHAR(150),
    hsn_code VARCHAR(50),
    receipt_no VARCHAR(80),
    sgst DECIMAL(12,2) DEFAULT 0,
    cgst DECIMAL(12,2) DEFAULT 0,
    service_tax DECIMAL(12,2) DEFAULT 0,
    total DECIMAL(12,2) NOT NULL,
    cgst_rate DECIMAL(5,2),
    sgst_rate DECIMAL(5,2),
    billed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Agencies
CREATE TABLE IF NOT EXISTS agencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(150),
    phone VARCHAR(20),
    address TEXT,
    license_number VARCHAR(120),
    license_photo LONGTEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Agency bills
CREATE TABLE IF NOT EXISTS agency_bills (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    agency_id BIGINT NOT NULL,
    agency_name VARCHAR(150) NOT NULL,
    shop_id BIGINT NOT NULL,
    shop_name VARCHAR(150) NOT NULL,
    bill_date DATE NOT NULL,
    total_amount DECIMAL(12,2) DEFAULT 0,
    payment_status ENUM('PENDING','PAID','CANCELLED') DEFAULT 'PENDING',
    items_json JSON,
    bill_photo LONGTEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Shop agencies (many-to-many)
CREATE TABLE IF NOT EXISTS shop_agencies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    shop_name VARCHAR(150) NOT NULL,
    agency_id BIGINT NOT NULL,
    agency_name VARCHAR(150) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Bill scans
CREATE TABLE IF NOT EXISTS bill_scans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    original_file_name VARCHAR(255),
    image_url LONGTEXT,
    cropped_image_url LONGTEXT,
    enhanced_image_url LONGTEXT,
    extracted_text LONGTEXT,
    parsed_items JSON,
    processing_status ENUM('PROCESSING','COMPLETED','FAILED') DEFAULT 'PROCESSING',
    processing_error TEXT,
    detection_bounds JSON,
    processing_steps JSON,
    extraction_confidence DECIMAL(5,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Invoices
CREATE TABLE IF NOT EXISTS invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    agency_id BIGINT NOT NULL,
    agency_name VARCHAR(150) NOT NULL,
    invoice_type VARCHAR(50) NOT NULL,
    invoice_no VARCHAR(120) NOT NULL UNIQUE,
    invoice_date DATE NOT NULL,
    due_date DATE,
    order_no VARCHAR(120),
    order_date DATE,
    cases INT,
    lr_no VARCHAR(120),
    lr_date DATE,
    transport VARCHAR(120),
    total_items INT,
    discount_amount DECIMAL(12,2) DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    total_taxable_amount DECIMAL(12,2) DEFAULT 0,
    tax_amount DECIMAL(12,2) DEFAULT 0,
    gross_amount DECIMAL(12,2) DEFAULT 0,
    net_amount DECIMAL(12,2) DEFAULT 0,
    other_charges DECIMAL(12,2) DEFAULT 0,
    supplier_name VARCHAR(150),
    dealer_name VARCHAR(150),
    company_name VARCHAR(150),
    customer_name VARCHAR(150),
    gst_number VARCHAR(20),
    pan_tin VARCHAR(20),
    payment_mode VARCHAR(50),
    bank_info TEXT,
    eway_bill_no VARCHAR(120),
    vehicle_no VARCHAR(120),
    transporter VARCHAR(150),
    supplier_json JSON,
    party_json JSON,
    bill_photo LONGTEXT,
    raw_text LONGTEXT,
    is_verified BOOLEAN DEFAULT FALSE,
    verified_at TIMESTAMP NULL,
    confidence_score DECIMAL(5,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Invoice items
CREATE TABLE IF NOT EXISTS invoice_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    shop_id BIGINT NOT NULL,
    s_no INT,
    product_name VARCHAR(150) NOT NULL,
    product_code VARCHAR(120),
    hsn_code VARCHAR(20),
    manufacturer VARCHAR(150),
    pack VARCHAR(50),
    quantity INT,
    unit VARCHAR(50),
    batch_no VARCHAR(120),
    expiry_date DATE,
    mrp DECIMAL(12,2),
    rate DECIMAL(12,2),
    discount DECIMAL(12,2) DEFAULT 0,
    discount_percent DECIMAL(5,2),
    gst_percent DECIMAL(5,2),
    cgst_rate DECIMAL(5,2),
    cgst_value DECIMAL(12,2) DEFAULT 0,
    sgst_rate DECIMAL(5,2),
    sgst_value DECIMAL(12,2) DEFAULT 0,
    igst_rate DECIMAL(5,2),
    igst_value DECIMAL(12,2) DEFAULT 0,
    taxable_value DECIMAL(12,2),
    amount DECIMAL(12,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Inventory
CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    medicine_name VARCHAR(150) NOT NULL,
    quantity INT DEFAULT 0,
    unit VARCHAR(50) DEFAULT 'strips',
    price DECIMAL(12,2) DEFAULT 0,
    quantity_in_base_units INT,
    pack_size INT DEFAULT 1,
    base_unit VARCHAR(50),
    pack VARCHAR(100),
    batch_no VARCHAR(100),
    expiry_date DATE,
    manufacturer VARCHAR(150),
    hsn_code VARCHAR(50),
    cgst_rate DECIMAL(5,2),
    sgst_rate DECIMAL(5,2),
    source_agency VARCHAR(150),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Chat messages
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(120),
    sender_type ENUM('CUSTOMER','SHOPKEEPER') NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Reviews
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(120),
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Notifications
CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    type ENUM('SHOP_UPDATE','MEDICINE_AVAILABLE','SHORTAGE_UPDATE','AGENCY_UPDATE','SYSTEM') DEFAULT 'SYSTEM',
    is_read BOOLEAN DEFAULT FALSE,
    related_id BIGINT,
    related_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Attribute Registry (for normalization)
CREATE TABLE IF NOT EXISTS attribute_registry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attribute_name VARCHAR(120) NOT NULL,
    canonical_name VARCHAR(120) NOT NULL,
    data_type VARCHAR(50) DEFAULT 'TEXT',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Invoice Attributes (dynamic dealer-specific fields)
CREATE TABLE IF NOT EXISTS invoice_attributes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id BIGINT NOT NULL,
    attribute_name VARCHAR(120) NOT NULL,
    attribute_value TEXT,
    confidence DECIMAL(5,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_bill_scans_shop ON bill_scans(shop_id);
CREATE INDEX idx_invoices_shop ON invoices(shop_id);
CREATE INDEX idx_invoice_items_invoice ON invoice_items(invoice_id);
CREATE INDEX idx_shortage_shop ON shortage_list(shop_id);
CREATE INDEX idx_shortage_customer ON shortage_list(customer_id);
CREATE INDEX idx_inventory_shop_medicine ON inventory(shop_id, medicine_name);
CREATE INDEX idx_chat_shop_customer ON chat_messages(shop_id, customer_id);
CREATE INDEX idx_reviews_shop ON reviews(shop_id);
CREATE INDEX idx_notifications_user ON notifications(user_id);
CREATE INDEX idx_attribute_registry_canonical ON attribute_registry(canonical_name);
CREATE INDEX idx_invoice_attributes_invoice ON invoice_attributes(invoice_id);
CREATE INDEX idx_shop_agencies_shop ON shop_agencies(shop_id);
CREATE INDEX idx_shop_agencies_agency ON shop_agencies(agency_id);
