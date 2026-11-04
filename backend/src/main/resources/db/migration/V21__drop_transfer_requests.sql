-- Bỏ bảng transfer_requests (V1 — BR-TRF-01..07, DM-14): luồng điều chuyển nhân viên giữa các chi nhánh
-- không làm nữa, entity TransferRequest / enum TransferRequestStatus đã xóa khỏi code. Không bảng nào trỏ
-- khóa ngoại tới bảng này, nên xóa thẳng; khóa ngoại và index của chính nó đi theo bảng.
DROP TABLE IF EXISTS transfer_requests;
