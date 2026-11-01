// Ảnh minh chứng đơn nghỉ (chốt 01/11/2026) — giới hạn khớp backend: tối đa 3 ảnh mỗi đơn, 5 MB mỗi ảnh.

export const MAX_LEAVE_IMAGES = 3;

/** Cạnh dài nhất sau khi thu nhỏ — đủ đọc rõ giấy khám bệnh, giấy mời chụp bằng điện thoại. */
const MAX_SIDE = 1600;

/**
 * Thu nhỏ một ảnh trước khi gửi: cạnh dài tối đa 1600px, JPEG chất lượng 0,85 — ảnh điện thoại 4–8 MB còn vài trăm
 * KB, dưới xa trần 5 MB của backend. Ảnh trình duyệt không đọc được (ví dụ HEIC trên Chrome) thì báo lỗi để người
 * dùng chọn ảnh JPG / PNG.
 *
 * @returns File JPEG mới, giữ tên gốc với đuôi .jpg
 */
export async function shrinkImage(file) {
  let bitmap;
  try {
    bitmap = await createImageBitmap(file);
  } catch {
    throw new Error(`Không đọc được ảnh «${file.name}» — chọn ảnh JPG hoặc PNG.`);
  }
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const width = Math.round(bitmap.width * scale);
  const height = Math.round(bitmap.height * scale);
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  // Ảnh PNG nền trong suốt đổi sang JPEG thì nền thành đen — tô trắng trước.
  context.fillStyle = '#fff';
  context.fillRect(0, 0, width, height);
  context.drawImage(bitmap, 0, 0, width, height);
  bitmap.close?.();
  const blob = await new Promise((resolve, reject) => {
    canvas.toBlob((result) => (result ? resolve(result) : reject(new Error('Không nén được ảnh.'))), 'image/jpeg', 0.85);
  });
  const name = `${file.name.replace(/\.[^.]+$/, '') || 'anh'}.jpg`;
  return new File([blob], name, { type: 'image/jpeg' });
}
