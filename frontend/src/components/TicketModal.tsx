import { apiBlob, apiRequest, TicketResponse } from '../api';
import { blobToDataUrl, formatDateTime, getErrorMessage } from '../utils/format';
import { StatusBadge } from './StatusBadge';
import { useEffect, useState } from 'react';

type TicketModalProps = {
  reservationId: number;
  onClose: () => void;
};

export function TicketModal({ reservationId, onClose }: TicketModalProps) {
  const [ticket, setTicket] = useState<TicketResponse | null>(null);
  const [qrUrl, setQrUrl] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      apiRequest<TicketResponse>(`/api/reservations/${reservationId}/ticket`),
      apiBlob(`/api/reservations/${reservationId}/ticket/qr`),
    ])
      .then(async ([ticketResponse, qrBlob]) => {
        setTicket(ticketResponse);
        setQrUrl(await blobToDataUrl(qrBlob));
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [reservationId]);

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <article className="ticket-modal">
        <button className="modal-close" type="button" onClick={onClose}>Close</button>
        <p className="eyebrow">CRYPTOCINEMA</p>
        {error && <p className="error-message">{error}</p>}
        {!ticket && !error && <p>Loading ticket...</p>}
        {ticket && (
          <>
            <h2>{ticket.movieTitle}</h2>
            <StatusBadge label="Ticket" status={ticket.ticketStatus} />
            {qrUrl && <img className="ticket-qr" src={qrUrl} alt="Ticket QR code" />}
            <code>{ticket.ticketCode}</code>
            <div className="ticket-details">
              <span>{formatDateTime(ticket.screeningStartTime)}</span>
              <span>{ticket.cinemaName} | {ticket.hallName}</span>
              <span>Seats: {ticket.seats.join(', ')}</span>
              <span>Total: {ticket.totalAmount} RSD</span>
              {ticket.usedAt && <span>Used: {formatDateTime(ticket.usedAt)}</span>}
            </div>
          </>
        )}
      </article>
    </div>
  );
}
