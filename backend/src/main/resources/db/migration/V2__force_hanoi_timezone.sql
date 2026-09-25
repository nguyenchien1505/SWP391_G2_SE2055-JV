-- Hệ thống chỉ chạy theo giờ Hà Nội: LocationService từ chối mọi múi giờ khác, nên đưa các
-- khách sạn đã lưu múi giờ thành phố khác về cùng một mốc (BR-SCH-17 đọc cột này).
UPDATE locations
SET timezone = 'Asia/Ho_Chi_Minh'
WHERE timezone <> 'Asia/Ho_Chi_Minh';
