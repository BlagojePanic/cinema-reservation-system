import { useEffect, useState } from 'react';
import { apiRequest, MovieResponse, PaymentResponse, ReservationResponse, UserResponse } from '../api';
import { Poster } from '../components/MovieCard';
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
      <section className="account-content">
        <h1>My Account</h1>
        <article className="account-info-card">
          <div className="account-card-heading">
            <div>
              <strong>{currentUser.firstName} {currentUser.lastName}</strong>
              <span>{currentUser.email}</span>
            </div>
            <StatusBadge status={currentUser.role} />
          </div>
          <div className="account-field-grid">
            <AccountField label="Name" value={`${currentUser.firstName} ${currentUser.lastName}`} />
            <AccountField label="First name" value={currentUser.firstName} />
            <AccountField label="Last name" value={currentUser.lastName} />
            <AccountField label="Email" value={currentUser.email} />
          </div>
        </article>
      </section>
    </main>
  );
}

export function ReservationsPage({ onNavigate, onLogout }: {
  onNavigate: (path: string) => void;
  onLogout: () => void;
}) {
  const [reservations, setReservations] = useState<ReservationResponse[]>([]);
  const [movies, setMovies] = useState<MovieResponse[]>([]);
  const [payments, setPayments] = useState<Record<number, PaymentResponse[]>>({});
  const [expandedId, setExpandedId] = useState<number | null>(null);
  const [ticketReservationId, setTicketReservationId] = useState<number | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    loadReservations();
  }, []);

  async function loadReservations() {
    try {
      const [response, movieResponse] = await Promise.all([
        apiRequest<ReservationResponse[]>('/api/reservations/me'),
        apiRequest<MovieResponse[]>('/api/movies'),
      ]);
      const pairs = await Promise.all(response.map(async (reservation) => {
        const reservationPayments = await apiRequest<PaymentResponse[]>(`/api/reservations/${reservation.reservationId}/payments`);
        return [reservation.reservationId, reservationPayments] as const;
      }));
      setReservations(response);
      setMovies(movieResponse);
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
      <section className="account-content">
        <h1>My Reservations</h1>
        <div className="reservation-list">
          {reservations.length === 0 ? <p>No reservations yet.</p> : reservations.map((reservation) => {
            const latestPayment = reservation.payment ?? payments[reservation.reservationId]?.[0] ?? null;
            const movie = movies.find((item) => item.title === reservation.movieTitle);
            const expanded = expandedId === reservation.reservationId;
            return (
              <ReservationCard
                expanded={expanded}
                key={reservation.reservationId}
                latestPayment={latestPayment}
                movie={movie}
                onCancel={() => cancelReservation(reservation.reservationId)}
                onPayChanged={loadReservations}
                onToggle={() => setExpandedId(expanded ? null : reservation.reservationId)}
                onViewTicket={() => setTicketReservationId(reservation.reservationId)}
                reservation={reservation}
              />
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
      <button type="button" onClick={onLogout}>Logout</button>
    </aside>
  );
}

function AccountField({ label, value }: { label: string; value: string }) {
  return (
    <div className="account-field">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function ReservationCard({
  expanded,
  latestPayment,
  movie,
  onCancel,
  onPayChanged,
  onToggle,
  onViewTicket,
  reservation,
}: {
  expanded: boolean;
  latestPayment: PaymentResponse | null;
  movie?: MovieResponse;
  onCancel: () => void;
  onPayChanged: () => void;
  onToggle: () => void;
  onViewTicket: () => void;
  reservation: ReservationResponse;
}) {
  return (
    <article className={`reservation-card reservation-card-${expanded ? 'expanded' : 'collapsed'}`}>
      <button className="reservation-summary" type="button" onClick={onToggle} aria-expanded={expanded}>
        <div className="reservation-poster">
          {movie ? <Poster movie={movie} /> : <span className="poster-placeholder">{reservation.movieTitle.charAt(0).toUpperCase()}</span>}
        </div>
        <div className="reservation-summary-copy">
          <strong>{reservation.movieTitle}</strong>
          <span>{formatDateTime(reservation.screeningStartTime)}</span>
          <span>{reservation.cinemaName}</span>
        </div>
        <div className="reservation-summary-meta">
          <span>{reservation.hallName}</span>
          <span>Seats: {reservation.seatLabels.join(', ')}</span>
        </div>
        <StatusBadge status={reservation.status} />
        <span className="reservation-chevron">{expanded ? 'Hide' : 'Details'}</span>
      </button>

      {expanded && (
        <div className="reservation-details">
          <div className="reservation-detail-poster">
            {movie ? <Poster movie={movie} /> : <span className="poster-placeholder">{reservation.movieTitle.charAt(0).toUpperCase()}</span>}
          </div>
          <div className="reservation-detail-body">
            <div className="card-heading">
              <div>
                <p className="eyebrow">{reservation.cinemaName}</p>
                <h2>{reservation.movieTitle}</h2>
              </div>
              <StatusBadge status={reservation.status} />
            </div>
            <div className="reservation-detail-grid">
              <InfoLine label="Screening" value={formatDateTime(reservation.screeningStartTime)} />
              <InfoLine label="Hall" value={reservation.hallName} />
              <InfoLine label="Seats" value={reservation.seatLabels.join(', ')} />
              <InfoLine label="Ticket price" value={`${reservation.seatLabels.length} x ${reservation.ticketPrice} RSD`} />
              <InfoLine label="Total amount" value={`${reservation.totalAmount} RSD`} strong />
              <InfoLine label="Reservation status" value={reservation.status} />
              <InfoLine label="Payment status" value={latestPayment?.status ?? 'No payment yet'} />
              <InfoLine label="Payment method" value={latestPayment?.method ?? '-'} />
              {reservation.status === 'PENDING_PAYMENT' && <InfoLine label="Expires" value={formatDateTime(reservation.expiresAt)} />}
            </div>
            <div className="reservation-actions">
              {reservation.status === 'PENDING_PAYMENT' && (
                <>
                  <PaymentPanel reservation={reservation} onChanged={onPayChanged} />
                  <button type="button" onClick={onCancel}>Cancel reservation</button>
                </>
              )}
              {reservation.status === 'CONFIRMED' && (
                <button type="button" onClick={onViewTicket}>View ticket</button>
              )}
            </div>
          </div>
        </div>
      )}
    </article>
  );
}

function InfoLine({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="info-line">
      <span>{label}</span>
      {strong ? <strong>{value}</strong> : <span>{value}</span>}
    </div>
  );
}
