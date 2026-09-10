type StatusBadgeProps = {
  label?: string;
  status: string | null;
};

export function StatusBadge({ label, status }: StatusBadgeProps) {
  const value = status ?? 'NONE';
  return (
    <span className={`status-badge status-${value.toLowerCase()}`}>
      {label ? `${label}: ` : ''}{value}
    </span>
  );
}
