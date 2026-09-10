import { FormEvent, ReactNode, useEffect, useState } from 'react';
import {
  apiRequest,
  AdminReservationResponse,
  AdminTicketResponse,
  CinemaResponse,
  CityResponse,
  HallResponse,
  MovieResponse,
  ScreeningResponse,
  SeatResponse,
  TicketValidationResponse,
} from '../api';
import { Poster } from '../components/MovieCard';
import { StatusBadge } from '../components/StatusBadge';
import { formatDateTime, getErrorMessage, optionalFormValue, shortAddress } from '../utils/format';

type AdminProps = {
  onNavigate: (path: string) => void;
};

export function AdminDashboardPage({ onNavigate }: AdminProps) {
  const [counts, setCounts] = useState({ movies: 0, cinemas: 0, screenings: 0, reservations: 0 });
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      apiRequest<MovieResponse[]>('/api/movies'),
      apiRequest<CinemaResponse[]>('/api/cinemas'),
      apiRequest<ScreeningResponse[]>('/api/screenings'),
      apiRequest<AdminReservationResponse[]>('/api/admin/reservations'),
    ])
      .then(([movies, cinemas, screenings, reservations]) => {
        setCounts({ movies: movies.length, cinemas: cinemas.length, screenings: screenings.length, reservations: reservations.length });
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, []);

  return (
    <AdminLayout onNavigate={onNavigate} title="Admin Dashboard">
      <div className="summary-grid">
        <SummaryCard label="Movies" value={counts.movies} />
        <SummaryCard label="Cinemas" value={counts.cinemas} />
        <SummaryCard label="Screenings" value={counts.screenings} />
        <SummaryCard label="Reservations" value={counts.reservations} />
      </div>
      {error && <p className="error-message">{error}</p>}
    </AdminLayout>
  );
}

export function AdminMoviesPage({ onNavigate }: AdminProps) {
  const [movies, setMovies] = useState<MovieResponse[]>([]);
  const [editingId, setEditingId] = useState('');
  const [posterFile, setPosterFile] = useState<File | null>(null);
  const [trailerFile, setTrailerFile] = useState<File | null>(null);
  const [busyMovieId, setBusyMovieId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const editingMovie = movies.find((movie) => movie.id === Number(editingId));

  useEffect(() => {
    refreshMovies();
  }, []);

  async function refreshMovies() {
    try {
      setMovies(await apiRequest<MovieResponse[]>('/api/admin/movies'));
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function submitMovie(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const movieId = String(form.get('movieId') ?? '');
    const path = movieId ? `/api/admin/movies/${movieId}` : '/api/admin/movies';
    const method = movieId ? 'PUT' : 'POST';

    try {
      let movie = await apiRequest<MovieResponse>(path, {
        method,
        body: JSON.stringify(movieBodyFromForm(form, editingMovie)),
      });
      if (posterFile) {
        movie = await uploadMovieFile(movie.id, 'poster', posterFile);
      }
      if (trailerFile) {
        movie = await uploadMovieFile(movie.id, 'trailer', trailerFile);
      }
      formElement.reset();
      setEditingId('');
      setPosterFile(null);
      setTrailerFile(null);
      setMessage(`Movie saved: ${movie.title}`);
      await refreshMovies();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function uploadMovieFile(movieId: number, kind: 'poster' | 'trailer', file: File) {
    const body = new FormData();
    body.append('file', file);
    return apiRequest<MovieResponse>(`/api/admin/movies/${movieId}/${kind}`, {
      method: 'POST',
      body,
    });
  }

  async function deleteMovie(movieId: number) {
    const movie = movies.find((item) => item.id === movieId);
    if (!window.confirm(`Are you sure you want to remove ${movie?.title ?? 'this movie'} from the active catalogue?`)) {
      return;
    }
    setBusyMovieId(movieId);
    try {
      await apiRequest(`/api/admin/movies/${movieId}`, { method: 'DELETE' });
      setEditingId('');
      setMessage('Movie removed from the active catalogue.');
      await refreshMovies();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setBusyMovieId(null);
    }
  }

  async function restoreMovie(movieId: number) {
    const movie = movies.find((item) => item.id === movieId);
    setBusyMovieId(movieId);
    try {
      await apiRequest<MovieResponse>(`/api/admin/movies/${movieId}/restore`, { method: 'POST' });
      setMessage(`Movie restored: ${movie?.title ?? movieId}`);
      await refreshMovies();
    } catch (err) {
      setError(getErrorMessage(err));
    } finally {
      setBusyMovieId(null);
    }
  }

  return (
    <AdminLayout onNavigate={onNavigate} title="Movies">
      <div className="admin-two-column">
        <form className="admin-form" onSubmit={submitMovie} key={editingMovie?.id ?? 'new-movie'}>
          <input name="movieId" type="hidden" defaultValue={editingMovie?.id ?? ''} />
          <label>Editing
            <select value={editingId} onChange={(event) => setEditingId(event.target.value)}>
              <option value="">New movie</option>
              {movies.map((movie) => <option key={movie.id} value={movie.id}>{movie.title}</option>)}
            </select>
          </label>
          <label>Title<input name="title" required defaultValue={editingMovie?.title ?? ''} /></label>
          <label>Description<textarea name="description" required defaultValue={editingMovie?.description ?? ''} /></label>
          <label>Genre<input name="genre" required defaultValue={editingMovie?.genre ?? ''} /></label>
          <label>Duration<input name="durationMinutes" type="number" min="1" required defaultValue={editingMovie?.durationMinutes ?? ''} /></label>
          <label>Age rating<input name="ageRating" defaultValue={editingMovie?.ageRating ?? ''} /></label>
          <label>Director<input name="director" defaultValue={editingMovie?.director ?? ''} /></label>
          <label>Release date<input name="releaseDate" type="date" defaultValue={editingMovie?.releaseDate ?? ''} /></label>
          <label>Poster<input type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => setPosterFile(event.target.files?.[0] ?? null)} /></label>
          {posterFile && <img className="upload-preview" src={URL.createObjectURL(posterFile)} alt="Selected poster preview" />}
          <label>Trailer<input type="file" accept="video/mp4,video/webm" onChange={(event) => setTrailerFile(event.target.files?.[0] ?? null)} /></label>
          {trailerFile && <span>Selected trailer: {trailerFile.name}</span>}
          <div className="actions">
            <button type="submit">{editingMovie ? 'Update movie' : 'Add movie'}</button>
            {editingMovie && editingMovie.status === 'ACTIVE' && (
              <button type="button" onClick={() => deleteMovie(editingMovie.id)} disabled={busyMovieId === editingMovie.id}>
                Archive movie
              </button>
            )}
            {editingMovie?.status === 'ARCHIVED' && (
              <button type="button" onClick={() => restoreMovie(editingMovie.id)} disabled={busyMovieId === editingMovie.id}>
                Restore movie
              </button>
            )}
          </div>
          {message && <p className="status-message">{message}</p>}
          {error && <p className="error-message">{error}</p>}
        </form>
        <div className="admin-list">
          {movies.map((movie) => (
            <article className="admin-list-item" key={movie.id}>
              <div className="mini-poster"><Poster movie={movie} /></div>
              <strong>{movie.title}</strong>
              <StatusBadge status={movie.status} />
              <span>{movie.genre} | {movie.durationMinutes} min</span>
              <div className="actions">
                <button type="button" onClick={() => setEditingId(String(movie.id))}>Edit</button>
                {movie.status === 'ARCHIVED' && (
                  <button type="button" onClick={() => restoreMovie(movie.id)} disabled={busyMovieId === movie.id}>Restore</button>
                )}
              </div>
              {movie.trailerUrl && <video className="admin-video" src={movie.trailerUrl} controls />}
            </article>
          ))}
        </div>
      </div>
    </AdminLayout>
  );
}

export function AdminStructurePage({ onNavigate }: AdminProps) {
  const [cities, setCities] = useState<CityResponse[]>([]);
  const [cinemas, setCinemas] = useState<CinemaResponse[]>([]);
  const [halls, setHalls] = useState<HallResponse[]>([]);
  const [seats, setSeats] = useState<SeatResponse[]>([]);
  const [selectedHallId, setSelectedHallId] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    refresh();
  }, []);

  useEffect(() => {
    if (!selectedHallId) {
      setSeats([]);
      return;
    }
    apiRequest<SeatResponse[]>(`/api/halls/${selectedHallId}/seats`).then(setSeats).catch((err) => setError(getErrorMessage(err)));
  }, [selectedHallId]);

  async function refresh() {
    try {
      const [nextCities, nextCinemas] = await Promise.all([
        apiRequest<CityResponse[]>('/api/cities'),
        apiRequest<CinemaResponse[]>('/api/cinemas'),
      ]);
      const hallGroups = await Promise.all(nextCinemas.map((cinema) => apiRequest<HallResponse[]>(`/api/cinemas/${cinema.id}/halls`)));
      setCities(nextCities);
      setCinemas(nextCinemas);
      setHalls(hallGroups.flat());
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function submit(path: string, body: object, form: HTMLFormElement) {
    try {
      await apiRequest(path, { method: 'POST', body: JSON.stringify(body) });
      form.reset();
      await refresh();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <AdminLayout onNavigate={onNavigate} title="Cinema Structure">
      <div className="admin-grid">
        <form className="admin-form" onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          submit('/api/admin/cities', { name: form.get('name') }, event.currentTarget);
        }}>
          <h3>Add city</h3><label>Name<input name="name" required /></label><button type="submit">Add city</button>
        </form>
        <form className="admin-form" onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          submit('/api/admin/cinemas', { name: form.get('name'), address: form.get('address'), cityId: Number(form.get('cityId')) }, event.currentTarget);
        }}>
          <h3>Add cinema</h3>
          <label>Name<input name="name" required /></label>
          <label>Address<input name="address" required /></label>
          <label>City<select name="cityId" required><option value="">Select city</option>{cities.map((city) => <option key={city.id} value={city.id}>{city.name}</option>)}</select></label>
          <button type="submit">Add cinema</button>
        </form>
        <form className="admin-form" onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          submit('/api/admin/halls', { name: form.get('name'), cinemaId: Number(form.get('cinemaId')) }, event.currentTarget);
        }}>
          <h3>Add hall</h3>
          <label>Name<input name="name" required /></label>
          <label>Cinema<select name="cinemaId" required><option value="">Select cinema</option>{cinemas.map((cinema) => <option key={cinema.id} value={cinema.id}>{cinema.name}</option>)}</select></label>
          <button type="submit">Add hall</button>
        </form>
        <form className="admin-form" onSubmit={(event) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          submit(`/api/admin/halls/${Number(form.get('hallId'))}/seats/generate`, { rows: Number(form.get('rows')), seatsPerRow: Number(form.get('seatsPerRow')) }, event.currentTarget);
        }}>
          <h3>Generate seats</h3>
          <label>Hall<select name="hallId" required><option value="">Select hall</option>{halls.map((hall) => <option key={hall.id} value={hall.id}>{hall.cinemaName} | {hall.name}</option>)}</select></label>
          <label>Rows<input name="rows" type="number" min="1" required /></label>
          <label>Seats per row<input name="seatsPerRow" type="number" min="1" required /></label>
          <button type="submit">Generate seats</button>
        </form>
      </div>
      <section className="content-section">
        <h3>Seats preview</h3>
        <label>Hall<select value={selectedHallId} onChange={(event) => setSelectedHallId(event.target.value)}>
          <option value="">Select hall</option>{halls.map((hall) => <option key={hall.id} value={hall.id}>{hall.cinemaName} | {hall.name}</option>)}
        </select></label>
        <div className="seat-grid">{seats.map((seat) => <span key={seat.id}>{seat.rowLabel}{seat.seatNumber}</span>)}</div>
      </section>
      {error && <p className="error-message">{error}</p>}
    </AdminLayout>
  );
}

export function AdminScreeningsPage({ onNavigate }: AdminProps) {
  const [movies, setMovies] = useState<MovieResponse[]>([]);
  const [cinemas, setCinemas] = useState<CinemaResponse[]>([]);
  const [halls, setHalls] = useState<HallResponse[]>([]);
  const [screenings, setScreenings] = useState<ScreeningResponse[]>([]);
  const [editingId, setEditingId] = useState('');
  const [busyScreeningId, setBusyScreeningId] = useState<number | null>(null);
  const [error, setError] = useState('');
  const editing = screenings.find((screening) => screening.id === Number(editingId));

  useEffect(() => { refresh(); }, []);

  async function refresh() {
    try {
      const [nextMovies, nextCinemas, nextScreenings] = await Promise.all([
        apiRequest<MovieResponse[]>('/api/movies'),
        apiRequest<CinemaResponse[]>('/api/cinemas'),
        apiRequest<ScreeningResponse[]>('/api/screenings'),
      ]);
      const hallGroups = await Promise.all(nextCinemas.map((cinema) => apiRequest<HallResponse[]>(`/api/cinemas/${cinema.id}/halls`)));
      setMovies(nextMovies);
      setCinemas(nextCinemas);
      setHalls(hallGroups.flat());
      setScreenings(nextScreenings);
      setError('');
    } catch (err) { setError(getErrorMessage(err)); }
  }

  async function submitScreening(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const id = String(form.get('screeningId') ?? '');
    try {
      await apiRequest(id ? `/api/admin/screenings/${id}` : '/api/admin/screenings', {
        method: id ? 'PUT' : 'POST',
        body: JSON.stringify({
          movieId: Number(form.get('movieId')),
          hallId: Number(form.get('hallId')),
          startTime: `${form.get('date')}T${form.get('time')}:00`,
          ticketPrice: Number(form.get('ticketPrice')),
        }),
      });
      formElement.reset();
      setEditingId('');
      await refresh();
    } catch (err) { setError(getErrorMessage(err)); }
  }

  async function deleteScreening(id: number) {
    const screening = screenings.find((item) => item.id === id);
    if (!window.confirm(`Are you sure you want to delete ${screening?.movieTitle ?? 'this screening'}?`)) {
      return;
    }
    setBusyScreeningId(id);
    try {
      await apiRequest(`/api/admin/screenings/${id}`, { method: 'DELETE' });
      setEditingId('');
      await refresh();
    } catch (err) { setError(getErrorMessage(err)); }
    finally { setBusyScreeningId(null); }
  }

  return (
    <AdminLayout onNavigate={onNavigate} title="Screenings">
      <div className="admin-two-column">
        <form className="admin-form" onSubmit={submitScreening} key={editing?.id ?? 'new-screening'}>
          <input name="screeningId" type="hidden" defaultValue={editing?.id ?? ''} />
          <label>Editing<select value={editingId} onChange={(event) => setEditingId(event.target.value)}>
            <option value="">New screening</option>{screenings.map((screening) => <option key={screening.id} value={screening.id}>{screening.movieTitle} | {formatDateTime(screening.startTime)}</option>)}
          </select></label>
          <label>Movie<select name="movieId" required defaultValue={editing?.movieId ?? ''}><option value="">Select movie</option>{movies.map((movie) => <option key={movie.id} value={movie.id}>{movie.title}</option>)}</select></label>
          <label>Hall<select name="hallId" required defaultValue={editing?.hallId ?? ''}><option value="">Select hall</option>{halls.map((hall) => <option key={hall.id} value={hall.id}>{hall.cinemaName} | {hall.name}</option>)}</select></label>
          <label>Date<input name="date" type="date" required defaultValue={editing?.startTime.slice(0, 10) ?? ''} /></label>
          <label>Time<input name="time" type="time" required defaultValue={editing?.startTime.slice(11, 16) ?? ''} /></label>
          <label>Ticket price<input name="ticketPrice" type="number" step="0.01" min="1" required defaultValue={editing?.ticketPrice ?? ''} /></label>
          <div className="actions"><button type="submit">{editing ? 'Update screening' : 'Add screening'}</button>{editing && <button type="button" onClick={() => deleteScreening(editing.id)} disabled={busyScreeningId === editing.id}>Delete screening</button>}</div>
        </form>
        <div className="admin-list">{screenings.map((screening) => <article className="admin-list-item" key={screening.id}><strong>{screening.movieTitle}</strong><span>{formatDateTime(screening.startTime)}</span><span>{screening.cinemaName} | {screening.hallName}</span><span>{screening.ticketPrice} RSD</span></article>)}</div>
      </div>
      {error && <p className="error-message">{error}</p>}
    </AdminLayout>
  );
}

export function AdminReservationsPage({ onNavigate }: AdminProps) {
  const [reservations, setReservations] = useState<AdminReservationResponse[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    apiRequest<AdminReservationResponse[]>('/api/admin/reservations').then(setReservations).catch((err) => setError(getErrorMessage(err)));
  }, []);

  return (
    <AdminLayout onNavigate={onNavigate} title="Reservations">
      <div className="admin-list">
        {reservations.map((reservation) => (
          <article className="admin-list-item" key={reservation.reservationId}>
            <strong>{reservation.userEmail}</strong>
            <span>{reservation.movieTitle} | {formatDateTime(reservation.startTime)}</span>
            <span>{reservation.cinemaName} | {reservation.hallName} | {reservation.seats.join(', ')}</span>
            <div className="inline-statuses">
              <StatusBadge label="Reservation" status={reservation.reservationStatus} />
              <StatusBadge label="Payment" status={reservation.latestPaymentStatus} />
            </div>
            <span>Method: {reservation.latestPaymentMethod ?? '-'}</span>
            {reservation.latestPaymentTransactionHash && <a href={`https://sepolia.etherscan.io/tx/${reservation.latestPaymentTransactionHash}`} target="_blank" rel="noreferrer">Tx: {shortAddress(reservation.latestPaymentTransactionHash)}</a>}
          </article>
        ))}
      </div>
      {error && <p className="error-message">{error}</p>}
    </AdminLayout>
  );
}

export function AdminTicketValidationPage({ onNavigate }: AdminProps) {
  const [ticket, setTicket] = useState<AdminTicketResponse | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function checkTicket(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const code = String(new FormData(event.currentTarget).get('ticketCode') ?? '').trim();
    try {
      setTicket(await apiRequest<AdminTicketResponse>(`/api/admin/tickets/${encodeURIComponent(code)}`));
      setMessage('');
      setError('');
    } catch (err) {
      setTicket(null);
      setError(getErrorMessage(err));
    }
  }

  async function validateEntry() {
    if (!ticket) return;
    try {
      const response = await apiRequest<TicketValidationResponse>(`/api/admin/tickets/${encodeURIComponent(ticket.ticketCode)}/validate`, { method: 'POST' });
      setTicket(response.ticket);
      setMessage(response.message);
      setError('');
    } catch (err) { setError(getErrorMessage(err)); }
  }

  return (
    <AdminLayout onNavigate={onNavigate} title="Ticket Validation">
      <form className="admin-form wide-form" onSubmit={checkTicket}>
        <label>Ticket code<input name="ticketCode" required /></label>
        <button type="submit">Check ticket</button>
      </form>
      {ticket && (
        <article className={`validation-result validation-${ticket.ticketStatus.toLowerCase()}`}>
          <StatusBadge label="Ticket" status={ticket.ticketStatus} />
          <StatusBadge label="Reservation" status={ticket.reservationStatus} />
          <strong>{ticket.movieTitle}</strong>
          <span>User: {ticket.userEmail}</span>
          <span>{formatDateTime(ticket.screeningStartTime)}</span>
          <span>{ticket.cinemaName} | {ticket.hallName}</span>
          <span>Seats: {ticket.seats.join(', ')}</span>
          <code>{ticket.ticketCode}</code>
          {ticket.usedAt && <span>Used: {formatDateTime(ticket.usedAt)}</span>}
          {ticket.ticketStatus === 'VALID' && ticket.reservationStatus === 'CONFIRMED' && <button type="button" onClick={validateEntry}>Validate Entry</button>}
        </article>
      )}
      {message && <p className="status-message">{message}</p>}
      {error && <p className="error-message">{error}</p>}
    </AdminLayout>
  );
}

function AdminLayout({ title, onNavigate, children }: AdminProps & { title: string; children: ReactNode }) {
  return (
    <main className="admin-layout page">
      <aside className="sidebar">
        <button type="button" onClick={() => onNavigate('/admin')}>Dashboard</button>
        <button type="button" onClick={() => onNavigate('/admin/movies')}>Movies</button>
        <button type="button" onClick={() => onNavigate('/admin/structure')}>Cinema Structure</button>
        <button type="button" onClick={() => onNavigate('/admin/screenings')}>Screenings</button>
        <button type="button" onClick={() => onNavigate('/admin/reservations')}>Reservations</button>
        <button type="button" onClick={() => onNavigate('/admin/tickets')}>Ticket Validation</button>
      </aside>
      <section className="admin-content">
        <h1>{title}</h1>
        {children}
      </section>
    </main>
  );
}

function SummaryCard({ label, value }: { label: string; value: number }) {
  return <article className="summary-card"><span>{label}</span><strong>{value}</strong></article>;
}

function movieBodyFromForm(form: FormData, editingMovie?: MovieResponse) {
  return {
    title: form.get('title'),
    description: form.get('description'),
    genre: form.get('genre'),
    durationMinutes: Number(form.get('durationMinutes')),
    ageRating: optionalFormValue(form.get('ageRating')),
    director: optionalFormValue(form.get('director')),
    releaseDate: optionalFormValue(form.get('releaseDate')),
    posterUrl: editingMovie?.posterUrl ?? null,
    trailerUrl: editingMovie?.trailerUrl ?? null,
  };
}
