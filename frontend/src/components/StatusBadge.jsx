// design.md mục 3: huy hiệu trạng thái luôn có CHỮ kèm màu, không chỉ dùng chấm tròn —
// để người khó phân biệt màu vẫn đọc được.
const STATUS = {
  OPERATIONAL: { label: 'Đang hoạt động', tone: 'green' },
  NOT_OPERATIONAL: { label: 'Chưa vận hành', tone: 'grey' },
};

export default function StatusBadge({ status }) {
  const meta = STATUS[status] ?? { label: status, tone: 'grey' };
  return <span className={`badge badge--${meta.tone}`}>{meta.label}</span>;
}
