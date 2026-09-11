import { ReactNode } from 'react';

export type SeatLayoutSeat = {
  id: number;
  rowLabel: string;
  seatNumber: number;
  className?: string;
  content?: ReactNode;
  disabled?: boolean;
  onClick?: () => void;
};

type SeatLayoutProps = {
  seats: SeatLayoutSeat[];
  neutral?: boolean;
};

export function SeatLayout({ seats, neutral = false }: SeatLayoutProps) {
  const groupedSeats = seats.reduce<Record<string, SeatLayoutSeat[]>>((groups, seat) => {
    groups[seat.rowLabel] = [...(groups[seat.rowLabel] ?? []), seat];
    return groups;
  }, {});

  return (
    <div className="seat-layout">
      <div className="screen-line">SCREEN</div>
      <div className="seat-map-scroll">
        <div className="seat-map-inner">
          {Object.entries(groupedSeats).map(([rowLabel, rowSeats]) => (
            <div className="seat-row" key={rowLabel}>
              <strong>{rowLabel}</strong>
              <div>
                {rowSeats.map((seat) => (
                  <button
                    className={`seat-button${neutral ? ' seat-neutral' : ''}${seat.className ? ` ${seat.className}` : ''}`}
                    key={seat.id}
                    type="button"
                    onClick={seat.onClick}
                    disabled={seat.disabled}
                  >
                    {seat.content ?? seat.seatNumber}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
