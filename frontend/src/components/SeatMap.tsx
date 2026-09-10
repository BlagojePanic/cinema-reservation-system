import { useEffect, useState } from 'react';
import {
  apiRequest,
  ReservationResponse,
  ScreeningResponse,
  ScreeningSeatResponse,
  UserResponse,
} from '../api';
import { formatDateTime, getErrorMessage } from '../utils/format';

type SeatMapProps = {
  screening: ScreeningResponse;
  currentUser: UserResponse | null;
  onReserved: (reservation: ReservationResponse) => void;
};

export function SeatMap({ screening, currentUser, onReserved }: SeatMapProps) {
  const [seats, setSeats] = useState<ScreeningSeatResponse[]>([]);
  const [message, setMessage] = useState('');

  useEffect(() => {
    loadSeats();
  }, [screening.id]);

  useEffect(() => {
    const intervalId = window.setInterval(loadSeats, 4000);
    return () => window.clearInterval(intervalId);
  }, [screening.id]);

  async function loadSeats() {
    try {
      setSeats(await apiRequest<ScreeningSeatResponse[]>(`/api/screenings/${screening.id}/seats`));
      setMessage('');
    } catch (err) {
      setMessage(getErrorMessage(err));
    }
  }

  async function toggleSeat(seat: ScreeningSeatResponse) {
    if (!currentUser) {
      setMessage('Please log in to select seats.');
      return;
    }
    if (seat.status === 'RESERVED' || (seat.status === 'HELD' && !seat.heldByCurrentUser)) {
      setMessage('This seat is no longer available.');
      return;
    }

    try {
      await apiRequest(`/api/screenings/${screening.id}/seats/${seat.screeningSeatId}/hold`, {
        method: seat.heldByCurrentUser ? 'DELETE' : 'POST',
      });
      await loadSeats();
    } catch (err) {
      setMessage(getErrorMessage(err));
      await loadSeats();
    }
  }

  async function reserveSelectedSeats() {
    const selectedSeats = seats.filter((seat) => seat.heldByCurrentUser);
    if (selectedSeats.length === 0) {
      setMessage('Select at least one seat.');
      return;
    }

    try {
      const reservation = await apiRequest<ReservationResponse>('/api/reservations', {
        method: 'POST',
        body: JSON.stringify({
          screeningId: screening.id,
          screeningSeatIds: selectedSeats.map((seat) => seat.screeningSeatId),
        }),
      });
      onReserved(reservation);
    } catch (err) {
      setMessage(getErrorMessage(err));
      await loadSeats();
    }
  }

  const groupedSeats = seats.reduce<Record<string, ScreeningSeatResponse[]>>((groups, seat) => {
    groups[seat.rowLabel] = [...(groups[seat.rowLabel] ?? []), seat];
    return groups;
  }, {});
  const selectedSeats = seats.filter((seat) => seat.heldByCurrentUser);
  const selectedLabels = selectedSeats.map((seat) => `${seat.rowLabel}${seat.seatNumber}`);

  return (
    <div className="seat-flow">
      <section className="seat-stage">
        <div className="screen-line">SCREEN</div>
        <div className="seat-map-scroll">
          {Object.entries(groupedSeats).map(([rowLabel, rowSeats]) => (
            <div className="seat-row" key={rowLabel}>
              <strong>{rowLabel}</strong>
              <div>
                {rowSeats.map((seat) => (
                  <button
                    className={`seat-button seat-${seat.status.toLowerCase()}${seat.heldByCurrentUser ? ' seat-owned' : ''}`}
                    key={seat.screeningSeatId}
                    type="button"
                    onClick={() => toggleSeat(seat)}
                  >
                    {seat.seatNumber}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
        <div className="seat-legend">
          <span>Available</span>
          <span>Held</span>
          <span>Reserved</span>
        </div>
      </section>
      <aside className="checkout-summary">
        <h3>{screening.movieTitle}</h3>
        <span>{formatDateTime(screening.startTime)}</span>
        <span>{screening.cinemaName} | {screening.hallName}</span>
        <span>Selected: {selectedLabels.length ? selectedLabels.join(', ') : 'None'}</span>
        <span>{selectedSeats.length} x {screening.ticketPrice} RSD</span>
        <strong>Total: {selectedSeats.length * screening.ticketPrice} RSD</strong>
        <button type="button" onClick={reserveSelectedSeats} disabled={!currentUser || selectedSeats.length === 0}>
          Continue
        </button>
        {!currentUser && <p className="error-message">Login is required for reservations.</p>}
        {message && <p className="error-message">{message}</p>}
      </aside>
    </div>
  );
}
