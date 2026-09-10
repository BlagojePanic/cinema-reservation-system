import { useEffect, useState } from 'react';
import { apiRequest, PaymentResponse, ReservationResponse, UserResponse } from '../api';
import { PaymentPanel } from '../components/PaymentPanel';
import { StatusBadge } from '../components/StatusBadge';
import { TicketModal } from '../components/TicketModal';
import { formatDateTime, getErrorMessage } from '../utils/format';

export function AccountPage({ currentUser, onNavigate, onLogout }: {
  currentUser: UserResponse | null;
  onNavigate: (path: string) => void;
  onLogout: () => void;
}) {
  if (!currentUser) {
    return <main className="page"><h1>My Account</h1><p>Login is required.</p></main>;
  }

  return (
    <main className="account-layout page">
      <AccountSidebar onNavigate={onNavigate} onLogout={onLogout} />
      <section className="account-panel">
        <h1>My Account</h1>
        <span>First name: {currentUser.firstName}</span>
        <span>Last name: {currentUser.lastName}</span>
        <span>Email: {currentUser.email}</span>
        <span>Role: {currentUser.role}</span>
      </section>
    </main>
  );
}

export function ReservationsPage({ onNavigate, onLogout }: {
  onNavigate: (path: string) => void;
  onLogout: () => void;
}) {
  const [reservations, setReservations] = useState<ReservationResponse[]>([]);
  const [payments, setPayments] = useState<Record<number, PaymentResponse[]>>({});
  const [ticketReservationId, setTicketReservationId] = useState<number | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    loadReservations();
  }, []);

  async function loadReservations() {
    try {
      const response = await apiRequest<ReservationResponse[]>('/api/reservations/me');
      const pairs = await Promise.all(response.map(async (reservation) => {
        const reservationPayments = await apiRequest<PaymentResponse[]>(`/api/reservations/${reservation.reservationId}/payments`);
        return [reservation.reservationId, reservationPayments] as const;
      }));
      setReservations(response);
      setPayments(Object.fromEntries(pairs));
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function cancelReservation(reservationId: number) {
    try {
      await apiRequest<ReservationResponse>(`/api/reservations/${reservationId}/cancel`, { method: 'POST' });
      setMessage('Reservation cancelled.');
      await loadReservations();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <main className="account-layout page">
      <AccountSidebar onNavigate={onNavigate} onLogout={onLogout} />
      <section className="account-panel">
        <h1>My Reservations</h1>
        <div className="reservation-list">
          {reservations.length === 0 ? <p>No reservations yet.</p> : reservations.map((reservation) => {
            const latestPayment = reservation.payment ?? payments[reservation.reservationId]?.[0] ?? null;
            return (
              <article className="reservation-card" key={reservation.reservationId}>
                <div className="card-heading">
                  <strong>{reservation.movieTitle}</strong>
                  <StatusBadge status={reservation.status} />
                </div>
                <span>{formatDateTime(reservation.screeningStartTime)}</span>
                <span>{reservation.cinemaName} | {reservation.hallName}</span>
                <span>Seats: {reservation.seatLabels.join(', ')}</span>
                <span>Total: {reservation.totalAmount} RSD</span>
                {reservation.status === 'PENDING_PAYMENT' && (
                  <span>Expires: {formatDateTime(reservation.expiresAt)}</span>
                )}
                {latestPayment && (
                  <div className="inline-statuses">
                    <StatusBadge label="Payment" status={latestPayment.status} />
                    <span>{latestPayment.method}</span>
                  </div>
                )}
                {reservation.status === 'PENDING_PAYMENT' && (
                  <>
                    <PaymentPanel reservationId={reservation.reservationId} onChanged={loadReservations} />
                    <button type="button" onClick={() => cancelReservation(reservation.reservationId)}>
                      Cancel reservation
                    </button>
                  </>
                )}
                {reservation.status === 'CONFIRMED' && (
                  <button type="button" onClick={() => setTicketReservationId(reservation.reservationId)}>
                    View ticket
                  </button>
                )}
              </article>
            );
          })}
        </div>
        {message && <p className="status-message">{message}</p>}
        {error && <p className="error-message">{error}</p>}
      </section>
      {ticketReservationId && (
        <TicketModal reservationId={ticketReservationId} onClose={() => setTicketReservationId(null)} />
      )}
    </main>
  );
}

export function TicketsPage({ onNavigate, onLogout }: {
  onNavigate: (path: string) => void;
  onLogout: () => void;
}) {
  const [reservations, setReservations] = useState<ReservationResponse[]>([]);
  const [ticketReservationId, setTicketReservationId] = useState<number | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    apiRequest<ReservationResponse[]>('/api/reservations/me')
      .then((response) => {
        setReservations(response.filter((reservation) => reservation.status === 'CONFIRMED'));
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, []);

  return (
    <main className="account-layout page">
      <AccountSidebar onNavigate={onNavigate} onLogout={onLogout} />
      <section className="account-panel">
        <h1>My Tickets</h1>
        <div className="reservation-list">
          {reservations.length === 0 ? <p>No tickets yet.</p> : reservations.map((reservation) => (
            <article className="reservation-card" key={reservation.reservationId}>
              <strong>{reservation.movieTitle}</strong>
              <span>{formatDateTime(reservation.screeningStartTime)}</span>
              <span>{reservation.cinemaName} | {reservation.hallName}</span>
              <span>Seats: {reservation.seatLabels.join(', ')}</span>
              <button type="button" onClick={() => setTicketReservationId(reservation.reservationId)}>
                View ticket
              </button>
            </article>
          ))}
        </div>
        {error && <p className="error-message">{error}</p>}
      </section>
      {ticketReservationId && (
        <TicketModal reservationId={ticketReservationId} onClose={() => setTicketReservationId(null)} />
      )}
    </main>
  );
}

function AccountSidebar({ onNavigate, onLogout }: {
  onNavigate: (path: string) => void;
  onLogout: () => void;
}) {
  return (
    <aside className="sidebar">
      <button type="button" onClick={() => onNavigate('/account')}>My Account</button>
      <button type="button" onClick={() => onNavigate('/reservations')}>My Reservations</button>
      <button type="button" onClick={() => onNavigate('/tickets')}>My Tickets</button>
      <button type="button" onClick={onLogout}>Logout</button>
    </aside>
  );
}
