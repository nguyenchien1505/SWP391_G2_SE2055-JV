const COLORS = ["bg-blue-600", "bg-violet-600", "bg-emerald-600", "bg-amber-600", "bg-rose-600", "bg-cyan-600"];

function initials(name: string) {
  const words = name.trim().split(/\s+/);
  const letters = words.length === 1 ? words[0].slice(0, 2) : words[0][0] + words[words.length - 1][0];
  return letters.toUpperCase();
}

export function TenantAvatar({ id, name, size = "md" }: { id: string; name: string; size?: "md" | "lg" }) {
  const color = COLORS[id.charCodeAt(0) % COLORS.length];
  const dim = size === "lg" ? "size-14 text-lg" : "size-10 text-sm";
  return (
    <div className={`flex shrink-0 items-center justify-center rounded-lg font-semibold text-white ${dim} ${color}`}>
      {initials(name)}
    </div>
  );
}

export const shortId = (id: string) => id.slice(0, 8).toUpperCase();
