import { FormEvent, useEffect, useState } from 'react';
import {
  apiBlob,
  apiRequest,
  AdminReservationResponse,
  AdminTicketResponse,
  AuthResponse,
  CinemaResponse,
  CityResponse,
  clearToken,
  CryptoPaymentPrepareResponse,
  getToken,
  HallResponse,
  MovieResponse,
  PaymentResponse,
  ReservationResponse,
  saveToken,
  ScreeningResponse,
  ScreeningSeatResponse,
  SeatResponse,
  TicketResponse,
  TicketValidationResponse,
  UserResponse,
} from './api';

type HealthState = 'checking' | 'up' | 'down';
type Page = 'home' | 'register' | 'login';
type CryptoUiStatus = 'READY' | 'WALLET_CONNECTED' | 'TRANSACTION_SENT' | 'WAITING_CONFIRMATION' | 'SUCCESS' | 'FAILED';

type CryptoUiState = {
  status: CryptoUiStatus;
  message: string;
  walletAddress?: string;
  transactionHash?: string;
  prepare?: CryptoPaymentPrepareResponse;
};

type EthereumProvider = {
  request<T = unknown>(args: { method: string; params?: unknown[] }): Promise<T>;
};

declare global {
  interface Window {
    ethereum?: EthereumProvider;
  }
}

function App() {
  const [health, setHealth] = useState<HealthState>('checking');
  const [page, setPage] = useState<Page>(() => getPageFromPath());
  const [currentUser, setCurrentUser] = useState<UserResponse | null>(null);
  const [statusMessage, setStatusMessage] = useState('');
  const [structureRefreshKey, setStructureRefreshKey] = useState(0);
  const [movieRefreshKey, setMovieRefreshKey] = useState(0);
  const [screeningRefreshKey, setScreeningRefreshKey] = useState(0);
  const [reservationRefreshKey, setReservationRefreshKey] = useState(0);

  useEffect(() => {
    fetch('/api/health')
      .then((response) => {
        if (!response.ok) {
          throw new Error('Backend health check failed');
        }
        return response.json() as Promise<{ status: string }>;
      })
      .then((data) => setHealth(data.status === 'UP' ? 'up' : 'down'))
      .catch(() => setHealth('down'));
  }, []);

  useEffect(() => {
    if (!getToken()) {
      return;
    }

    apiRequest<UserResponse>('/api/auth/me')
      .then((user) => setCurrentUser(user))
      .catch(() => {
        clearToken();
        setCurrentUser(null);
      });
  }, []);

  useEffect(() => {
    const onPopState = () => setPage(getPageFromPath());
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);

  const message =
    health === 'checking'
      ? 'Checking backend status...'
      : health === 'up'
        ? 'Backend is available.'
        : 'Backend is not available.';

  function navigate(nextPage: Page, clearStatus = true) {
    const path = nextPage === 'home' ? '/' : `/${nextPage}`;
    window.history.pushState({}, '', path);
    setPage(nextPage);
    if (clearStatus) {
      setStatusMessage('');
    }
  }

  async function testEndpoint(path: '/api/user/test' | '/api/admin/test') {
    setStatusMessage('Sending authenticated request...');
    try {
      const response = await apiRequest<{ message: string }>(path);
      setStatusMessage(response.message);
    } catch (error) {
      setStatusMessage(getErrorMessage(error));
    }
  }

  function logout() {
    clearToken();
    setCurrentUser(null);
    setStatusMessage('Token removed from localStorage.');
  }

  function refreshStructureData() {
    setStructureRefreshKey((value) => value + 1);
  }

  function refreshMovieData() {
    setMovieRefreshKey((value) => value + 1);
  }

  function refreshScreeningData() {
    setScreeningRefreshKey((value) => value + 1);
  }

  function refreshReservationData() {
    setReservationRefreshKey((value) => value + 1);
  }

  function refreshAllData() {
    refreshStructureData();
    refreshMovieData();
    refreshScreeningData();
    refreshReservationData();
  }

  return (
    <main className="app-shell">
      <section className="status-panel">
        <p className="eyebrow">CryptoCinema</p>
        <nav className="nav-row" aria-label="Primary navigation">
          <button type="button" onClick={() => navigate('home')}>
            Home
          </button>
          <button type="button" onClick={() => navigate('register')}>
            Register
          </button>
          <button type="button" onClick={() => navigate('login')}>
            Login
          </button>
        </nav>

        {page === 'home' && (
          <>
            <h1>Authentication test</h1>
            <p>{message}</p>
            <div className="actions">
              <button type="button" onClick={() => testEndpoint('/api/user/test')}>
                Test USER endpoint
              </button>
              <button type="button" onClick={() => testEndpoint('/api/admin/test')}>
                Test ADMIN endpoint
              </button>
              <button type="button" onClick={logout}>
                Logout
              </button>
            </div>
            {currentUser && (
              <p className="session">
                Logged in as {currentUser.email} ({currentUser.role})
              </p>
            )}
            {getToken() && !currentUser && <p className="session">JWT is saved in localStorage.</p>}
            <MovieBrowser
              refreshKey={movieRefreshKey}
              screeningRefreshKey={screeningRefreshKey}
              currentUser={currentUser}
              onReservationChanged={refreshAllData}
            />
            <RepertoireBrowser
              refreshKey={screeningRefreshKey}
              currentUser={currentUser}
              onReservationChanged={refreshAllData}
            />
            {currentUser && (
              <MyReservations refreshKey={reservationRefreshKey} onChanged={refreshAllData} />
            )}
            {currentUser?.role === 'ADMIN' && (
              <AdminReservationPanel refreshKey={reservationRefreshKey} />
            )}
            {currentUser?.role === 'ADMIN' && (
              <AdminTicketValidationPanel />
            )}
            {currentUser?.role === 'ADMIN' && (
              <AdminMoviePanel
                refreshKey={movieRefreshKey}
                onChanged={() => {
                  refreshAllData();
                  setStatusMessage('Movie catalog updated.');
                }}
              />
            )}
            <CatalogBrowser refreshKey={structureRefreshKey} />
            {currentUser?.role === 'ADMIN' && (
              <AdminStructurePanel
                refreshKey={structureRefreshKey}
                onChanged={() => {
                  refreshAllData();
                  setStatusMessage('Cinema structure updated.');
                }}
              />
            )}
            {currentUser?.role === 'ADMIN' && (
              <AdminScreeningPanel
                refreshKey={screeningRefreshKey}
                onChanged={() => {
                  refreshAllData();
                  setStatusMessage('Screening schedule updated.');
                }}
              />
            )}
          </>
        )}

        {page === 'register' && (
          <RegisterForm
            onRegistered={(user) => {
              setCurrentUser(user);
              setStatusMessage(`Registered ${user.email} with role ${user.role}.`);
            }}
          />
        )}

        {page === 'login' && (
          <LoginForm
            onLoggedIn={(auth) => {
              saveToken(auth.token);
              setCurrentUser(auth.user);
              setStatusMessage(`Logged in as ${auth.user.email}.`);
              navigate('home', false);
            }}
          />
        )}

        {statusMessage && <p className="status-message">{statusMessage}</p>}
      </section>
    </main>
  );
}

function RegisterForm({ onRegistered }: { onRegistered: (user: UserResponse) => void }) {
  const [error, setError] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    const formElement = event.currentTarget;
    const form = new FormData(formElement);

    try {
      const user = await apiRequest<UserResponse>('/api/auth/register', {
        method: 'POST',
        body: JSON.stringify({
          firstName: form.get('firstName'),
          lastName: form.get('lastName'),
          email: form.get('email'),
          password: form.get('password'),
        }),
      });
      formElement.reset();
      onRegistered(user);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <>
      <h1>Register</h1>
      <form className="form-grid" onSubmit={handleSubmit}>
        <label>
          First name
          <input name="firstName" required />
        </label>
        <label>
          Last name
          <input name="lastName" required />
        </label>
        <label>
          Email
          <input name="email" type="email" required />
        </label>
        <label>
          Password
          <input name="password" type="password" minLength={8} required />
        </label>
        <button type="submit">Create account</button>
      </form>
      {error && <p className="error-message">{error}</p>}
    </>
  );
}

function LoginForm({ onLoggedIn }: { onLoggedIn: (auth: AuthResponse) => void }) {
  const [error, setError] = useState('');

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    const formElement = event.currentTarget;
    const form = new FormData(formElement);

    try {
      const auth = await apiRequest<AuthResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify({
          email: form.get('email'),
          password: form.get('password'),
        }),
      });
      formElement.reset();
      onLoggedIn(auth);
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <>
      <h1>Login</h1>
      <form className="form-grid" onSubmit={handleSubmit}>
        <label>
          Email
          <input name="email" type="email" required />
        </label>
        <label>
          Password
          <input name="password" type="password" required />
        </label>
        <button type="submit">Login</button>
      </form>
      {error && <p className="error-message">{error}</p>}
    </>
  );
}

function MovieBrowser({
  refreshKey,
  screeningRefreshKey,
  currentUser,
  onReservationChanged,
}: {
  refreshKey: number;
  screeningRefreshKey: number;
  currentUser: UserResponse | null;
  onReservationChanged: () => void;
}) {
  const [movies, setMovies] = useState<MovieResponse[]>([]);
  const [selectedMovie, setSelectedMovie] = useState<MovieResponse | null>(null);
  const [selectedMovieScreenings, setSelectedMovieScreenings] = useState<ScreeningResponse[]>([]);
  const [selectedScreening, setSelectedScreening] = useState<ScreeningResponse | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    apiRequest<MovieResponse[]>('/api/movies')
      .then((response) => {
        setMovies(response);
        setSelectedMovie((current) =>
          current ? response.find((movie) => movie.id === current.id) ?? null : null,
        );
        setSelectedScreening(null);
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [refreshKey]);

  useEffect(() => {
    if (!selectedMovie) {
      setSelectedMovieScreenings([]);
      return;
    }

    apiRequest<ScreeningResponse[]>(`/api/movies/${selectedMovie.id}/screenings`)
      .then((response) => {
        setSelectedMovieScreenings(response);
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [selectedMovie, screeningRefreshKey]);

  async function openDetails(movieId: number) {
    try {
      const movie = await apiRequest<MovieResponse>(`/api/movies/${movieId}`);
      const screenings = await apiRequest<ScreeningResponse[]>(`/api/movies/${movieId}/screenings`);
      setSelectedMovie(movie);
      setSelectedMovieScreenings(screenings);
      setSelectedScreening(null);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <section className="data-section">
      <h2>Movies</h2>
      {movies.length === 0 ? (
        <p>No movies added yet.</p>
      ) : (
        <div className="movie-grid">
          {movies.map((movie) => (
            <button
              className="movie-card"
              key={movie.id}
              type="button"
              onClick={() => openDetails(movie.id)}
            >
              {movie.posterUrl ? (
                <img src={movie.posterUrl} alt={`${movie.title} poster`} />
              ) : (
                <span className="poster-placeholder">{movie.title.charAt(0).toUpperCase()}</span>
              )}
              <strong>{movie.title}</strong>
              <span>{movie.genre}</span>
              <span>{movie.durationMinutes} min{movie.ageRating ? ` - ${movie.ageRating}` : ''}</span>
            </button>
          ))}
        </div>
      )}

      {selectedMovie && (
        <article className="movie-detail">
          {selectedMovie.posterUrl && (
            <img src={selectedMovie.posterUrl} alt={`${selectedMovie.title} poster`} />
          )}
          <div>
            <h3>{selectedMovie.title}</h3>
            <p>{selectedMovie.description}</p>
            <dl>
              <div>
                <dt>Genre</dt>
                <dd>{selectedMovie.genre}</dd>
              </div>
              <div>
                <dt>Duration</dt>
                <dd>{selectedMovie.durationMinutes} min</dd>
              </div>
              {selectedMovie.ageRating && (
                <div>
                  <dt>Age rating</dt>
                  <dd>{selectedMovie.ageRating}</dd>
                </div>
              )}
              {selectedMovie.director && (
                <div>
                  <dt>Director</dt>
                  <dd>{selectedMovie.director}</dd>
                </div>
              )}
              {selectedMovie.releaseDate && (
                <div>
                  <dt>Release date</dt>
                  <dd>{selectedMovie.releaseDate}</dd>
                </div>
              )}
            </dl>
            {selectedMovie.trailerUrl && (
              <a href={selectedMovie.trailerUrl} target="_blank" rel="noreferrer">
                Trailer
              </a>
            )}
            <MovieScreeningList screenings={selectedMovieScreenings} onSelect={setSelectedScreening} />
            {selectedScreening && (
              <SeatMap
                screening={selectedScreening}
                currentUser={currentUser}
                refreshKey={screeningRefreshKey}
                onReservationChanged={onReservationChanged}
              />
            )}
          </div>
        </article>
      )}

      {error && <p className="error-message">{error}</p>}
    </section>
  );
}

function MovieScreeningList({
  screenings,
  onSelect,
}: {
  screenings: ScreeningResponse[];
  onSelect: (screening: ScreeningResponse) => void;
}) {
  const groupedScreenings = screenings.reduce<Record<string, ScreeningResponse[]>>((groups, screening) => {
    const date = screening.startTime.slice(0, 10);
    groups[date] = [...(groups[date] ?? []), screening];
    return groups;
  }, {});

  return (
    <section className="screening-block">
      <h4>Screenings</h4>
      {screenings.length === 0 ? (
        <p>No screenings available.</p>
      ) : (
        <div className="screening-groups">
          {Object.entries(groupedScreenings).map(([date, dailyScreenings]) => (
            <div className="screening-day" key={date}>
              <strong>{formatDate(date)}</strong>
              {dailyScreenings.map((screening) => (
                <button
                  className="screening-time"
                  key={screening.id}
                  type="button"
                  onClick={() => onSelect(screening)}
                >
                  {screening.cinemaName} - {screening.hallName} - {formatTime(screening.startTime)}
                </button>
              ))}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function RepertoireBrowser({
  refreshKey,
  currentUser,
  onReservationChanged,
}: {
  refreshKey: number;
  currentUser: UserResponse | null;
  onReservationChanged: () => void;
}) {
  const [cities, setCities] = useState<CityResponse[]>([]);
  const [cinemas, setCinemas] = useState<CinemaResponse[]>([]);
  const [movies, setMovies] = useState<MovieResponse[]>([]);
  const [screenings, setScreenings] = useState<ScreeningResponse[]>([]);
  const [selectedScreening, setSelectedScreening] = useState<ScreeningResponse | null>(null);
  const [selectedCityId, setSelectedCityId] = useState('');
  const [selectedCinemaId, setSelectedCinemaId] = useState('');
  const [selectedMovieId, setSelectedMovieId] = useState('');
  const [selectedDate, setSelectedDate] = useState('');
  const [error, setError] = useState('');

  const availableCinemas = selectedCityId
    ? cinemas.filter((cinema) => cinema.city.id === Number(selectedCityId))
    : cinemas;

  useEffect(() => {
    Promise.all([
      apiRequest<CityResponse[]>('/api/cities'),
      apiRequest<CinemaResponse[]>('/api/cinemas'),
      apiRequest<MovieResponse[]>('/api/movies'),
    ])
      .then(([nextCities, nextCinemas, nextMovies]) => {
        setCities(nextCities);
        setCinemas(nextCinemas);
        setMovies(nextMovies);
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [refreshKey]);

  useEffect(() => {
    const params = new URLSearchParams();
    if (selectedCityId) params.set('cityId', selectedCityId);
    if (selectedCinemaId) params.set('cinemaId', selectedCinemaId);
    if (selectedMovieId) params.set('movieId', selectedMovieId);
    if (selectedDate) params.set('date', selectedDate);

    const query = params.toString();
    apiRequest<ScreeningResponse[]>(`/api/screenings${query ? `?${query}` : ''}`)
      .then((response) => {
        setScreenings(response);
        setSelectedScreening((current) =>
          current ? response.find((screening) => screening.id === current.id) ?? null : null,
        );
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [refreshKey, selectedCityId, selectedCinemaId, selectedMovieId, selectedDate]);

  useEffect(() => {
    if (selectedCinemaId && !availableCinemas.some((cinema) => cinema.id === Number(selectedCinemaId))) {
      setSelectedCinemaId('');
    }
  }, [availableCinemas, selectedCinemaId]);

  return (
    <section className="data-section">
      <h2>Repertoire</h2>
      <div className="form-grid filters-grid">
        <label>
          City
          <select value={selectedCityId} onChange={(event) => setSelectedCityId(event.target.value)}>
            <option value="">All cities</option>
            {cities.map((city) => (
              <option key={city.id} value={city.id}>
                {city.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Cinema
          <select value={selectedCinemaId} onChange={(event) => setSelectedCinemaId(event.target.value)}>
            <option value="">All cinemas</option>
            {availableCinemas.map((cinema) => (
              <option key={cinema.id} value={cinema.id}>
                {cinema.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Date
          <input type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} />
        </label>
        <label>
          Movie
          <select value={selectedMovieId} onChange={(event) => setSelectedMovieId(event.target.value)}>
            <option value="">All movies</option>
            {movies.map((movie) => (
              <option key={movie.id} value={movie.id}>
                {movie.title}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="screening-list">
        {screenings.length === 0 ? (
          <p>No screenings available.</p>
        ) : (
          screenings.map((screening) => (
            <article className="screening-card" key={screening.id}>
              <strong>{screening.movieTitle}</strong>
              <span>{formatDate(screening.startTime)} at {formatTime(screening.startTime)}</span>
              <span>{screening.cityName} - {screening.cinemaName} - {screening.hallName}</span>
              <span>{screening.ticketPrice} RSD</span>
              <button type="button" onClick={() => setSelectedScreening(screening)}>
                View seats
              </button>
            </article>
          ))
        )}
      </div>

      {selectedScreening && (
        <SeatMap
          screening={selectedScreening}
          currentUser={currentUser}
          refreshKey={refreshKey}
          onReservationChanged={onReservationChanged}
        />
      )}

      {error && <p className="error-message">{error}</p>}
    </section>
  );
}

function SeatMap({
  screening,
  currentUser,
  refreshKey,
  onReservationChanged,
}: {
  screening: ScreeningResponse;
  currentUser: UserResponse | null;
  refreshKey: number;
  onReservationChanged: () => void;
}) {
  const [seats, setSeats] = useState<ScreeningSeatResponse[]>([]);
  const [message, setMessage] = useState('');

  useEffect(() => {
    loadSeats();
  }, [screening.id, refreshKey]);

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      loadSeats();
    }, 4000);

    return () => window.clearInterval(intervalId);
  }, [screening.id]);

  async function loadSeats() {
    try {
      const response = await apiRequest<ScreeningSeatResponse[]>(`/api/screenings/${screening.id}/seats`);
      setSeats(response);
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

    if (seat.status === 'HELD' && !seat.heldByCurrentUser) {
      setMessage('This seat is no longer available.');
      return;
    }

    if (seat.status === 'RESERVED') {
      setMessage('This seat is no longer available.');
      return;
    }

    const method = seat.heldByCurrentUser ? 'DELETE' : 'POST';
    try {
      await apiRequest(`/api/screenings/${screening.id}/seats/${seat.screeningSeatId}/hold`, {
        method,
      });
      await loadSeats();
    } catch (err) {
      setMessage(getErrorMessage(err) === 'This seat is no longer available.'
        ? 'This seat is no longer available.'
        : getErrorMessage(err));
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
      setMessage(`Reservation created: ${reservation.status}. Expires at ${formatDateTime(reservation.expiresAt)}.`);
      await loadSeats();
      onReservationChanged();
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
  const total = selectedSeats.length * screening.ticketPrice;

  return (
    <section className="seat-map">
      <h4>{screening.movieTitle} seats</h4>
      <div className="screen-line">SCREEN</div>
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
      <div className="seat-legend">
        <span>AVAILABLE</span>
        <span>HELD</span>
        <span>RESERVED</span>
      </div>
      {currentUser && (
        <div className="reservation-summary">
          <strong>Selected seats: {selectedLabels.length ? selectedLabels.join(', ') : 'None'}</strong>
          <span>{selectedSeats.length} x {screening.ticketPrice} RSD</span>
          <span>Total: {total} RSD</span>
          <button type="button" onClick={reserveSelectedSeats} disabled={selectedSeats.length === 0}>
            Reserve seats
          </button>
        </div>
      )}
      {message && <p className="error-message">{message}</p>}
    </section>
  );
}

function MyReservations({
  refreshKey,
  onChanged,
}: {
  refreshKey: number;
  onChanged: () => void;
}) {
  const [reservations, setReservations] = useState<ReservationResponse[]>([]);
  const [paymentsByReservation, setPaymentsByReservation] = useState<Record<number, PaymentResponse[]>>({});
  const [payingReservationId, setPayingReservationId] = useState<number | null>(null);
  const [cryptoStates, setCryptoStates] = useState<Record<number, CryptoUiState>>({});
  const [selectedTicket, setSelectedTicket] = useState<TicketResponse | null>(null);
  const [ticketQrUrl, setTicketQrUrl] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    loadReservations();
  }, [refreshKey]);

  async function loadReservations() {
    try {
      const response = await apiRequest<ReservationResponse[]>('/api/reservations/me');
      const paymentPairs = await Promise.all(
        response.map(async (reservation) => {
          const payments = await apiRequest<PaymentResponse[]>(
            `/api/reservations/${reservation.reservationId}/payments`,
          );
          return [reservation.reservationId, payments] as const;
        }),
      );
      setReservations(response);
      setPaymentsByReservation(Object.fromEntries(paymentPairs));
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function cancelReservation(reservationId: number) {
    try {
      await apiRequest<ReservationResponse>(`/api/reservations/${reservationId}/cancel`, {
        method: 'POST',
      });
      setMessage('Reservation cancelled.');
      await loadReservations();
      onChanged();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function payReservation(reservationId: number, simulateSuccess: boolean) {
    setPayingReservationId(reservationId);
    setError('');
    setMessage('');
    try {
      const payment = await apiRequest<PaymentResponse>(`/api/reservations/${reservationId}/payment`, {
        method: 'POST',
        body: JSON.stringify({
          method: 'CARD_SIMULATION',
          simulateSuccess,
        }),
      });
      setMessage(
        payment.status === 'SUCCESS'
          ? 'Payment successful'
          : 'Payment failed. Please try again.',
      );
      await loadReservations();
      onChanged();
    } catch (err) {
      setError(getErrorMessage(err));
      await loadReservations();
    } finally {
      setPayingReservationId(null);
    }
  }

  async function payWithMetaMask(reservationId: number) {
    setPayingReservationId(reservationId);
    setError('');
    setMessage('');
    updateCryptoState(reservationId, { status: 'READY', message: 'Preparing MetaMask payment.' });

    try {
      const ethereum = window.ethereum;
      if (!ethereum) {
        updateCryptoState(reservationId, { status: 'FAILED', message: 'MetaMask is not installed.' });
        return;
      }

      const accounts = await ethereum.request<string[]>({ method: 'eth_requestAccounts' });
      const walletAddress = accounts[0];
      updateCryptoState(reservationId, {
        status: 'WALLET_CONNECTED',
        message: 'Wallet connected.',
        walletAddress,
      });

      const chainId = await ethereum.request<string>({ method: 'eth_chainId' });
      if (chainId.toLowerCase() !== '0xaa36a7') {
        await ethereum.request({
          method: 'wallet_switchEthereumChain',
          params: [{ chainId: '0xaa36a7' }],
        });
      }

      const prepare = await apiRequest<CryptoPaymentPrepareResponse>(
        `/api/reservations/${reservationId}/crypto-payment/prepare`,
        { method: 'POST' },
      );
      updateCryptoState(reservationId, {
        status: 'WALLET_CONNECTED',
        message: `Ready to send ${prepare.cryptoAmount} ${prepare.cryptoCurrency}.`,
        walletAddress,
        prepare,
      });

      const transactionHash = await ethereum.request<string>({
        method: 'eth_sendTransaction',
        params: [{
          from: walletAddress,
          to: prepare.merchantAddress,
          value: decimalEthToWeiHex(prepare.cryptoAmount),
        }],
      });
      updateCryptoState(reservationId, {
        status: 'TRANSACTION_SENT',
        message: 'Transaction sent. Waiting for backend verification.',
        walletAddress,
        transactionHash,
        prepare,
      });

      updateCryptoState(reservationId, {
        status: 'WAITING_CONFIRMATION',
        message: 'Waiting confirmation.',
        walletAddress,
        transactionHash,
        prepare,
      });
      const payment = await apiRequest<PaymentResponse>(
        `/api/reservations/${reservationId}/crypto-payment/confirm`,
        {
          method: 'POST',
          body: JSON.stringify({
            paymentId: prepare.paymentId,
            transactionHash,
            walletAddress,
          }),
        },
      );

      if (payment.status === 'SUCCESS') {
        updateCryptoState(reservationId, {
          status: 'SUCCESS',
          message: 'Payment successful',
          walletAddress,
          transactionHash,
          prepare,
        });
        setMessage('Payment successful');
      } else {
        updateCryptoState(reservationId, {
          status: 'WAITING_CONFIRMATION',
          message: 'Transaction is still pending. Try verification again in a few seconds.',
          walletAddress,
          transactionHash,
          prepare,
        });
      }
      await loadReservations();
      onChanged();
    } catch (err) {
      updateCryptoState(reservationId, {
        status: 'FAILED',
        message: getErrorMessage(err),
      });
      setError(getErrorMessage(err));
      await loadReservations();
    } finally {
      setPayingReservationId(null);
    }
  }

  async function verifyCryptoTransaction(reservationId: number) {
    const cryptoState = cryptoStates[reservationId];
    if (!cryptoState?.prepare || !cryptoState.transactionHash || !cryptoState.walletAddress) {
      setError('No pending crypto transaction is available for verification.');
      return;
    }

    setPayingReservationId(reservationId);
    setError('');
    updateCryptoState(reservationId, {
      ...cryptoState,
      status: 'WAITING_CONFIRMATION',
      message: 'Waiting confirmation.',
    });

    try {
      const payment = await apiRequest<PaymentResponse>(
        `/api/reservations/${reservationId}/crypto-payment/confirm`,
        {
          method: 'POST',
          body: JSON.stringify({
            paymentId: cryptoState.prepare.paymentId,
            transactionHash: cryptoState.transactionHash,
            walletAddress: cryptoState.walletAddress,
          }),
        },
      );
      updateCryptoState(reservationId, {
        ...cryptoState,
        status: payment.status === 'SUCCESS' ? 'SUCCESS' : 'WAITING_CONFIRMATION',
        message: payment.status === 'SUCCESS'
          ? 'Payment successful'
          : 'Transaction is still pending. Try verification again in a few seconds.',
      });
      await loadReservations();
      onChanged();
    } catch (err) {
      updateCryptoState(reservationId, {
        ...cryptoState,
        status: 'FAILED',
        message: getErrorMessage(err),
      });
      setError(getErrorMessage(err));
      await loadReservations();
    } finally {
      setPayingReservationId(null);
    }
  }

  function updateCryptoState(reservationId: number, state: CryptoUiState) {
    setCryptoStates((current) => ({
      ...current,
      [reservationId]: state,
    }));
  }

  async function viewTicket(reservationId: number) {
    setError('');
    setMessage('');
    try {
      const [ticket, qrBlob] = await Promise.all([
        apiRequest<TicketResponse>(`/api/reservations/${reservationId}/ticket`),
        apiBlob(`/api/reservations/${reservationId}/ticket/qr`),
      ]);
      setSelectedTicket(ticket);
      setTicketQrUrl(await blobToDataUrl(qrBlob));
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  function closeTicket() {
    setSelectedTicket(null);
    setTicketQrUrl('');
  }

  return (
    <section className="data-section">
      <h2>My Reservations</h2>
      <div className="reservation-list">
        {reservations.length === 0 ? (
          <p>No reservations yet.</p>
        ) : (
          reservations.map((reservation) => {
            const payments = paymentsByReservation[reservation.reservationId] ?? [];
            const latestPayment = reservation.payment ?? payments[0] ?? null;
            const cryptoState = cryptoStates[reservation.reservationId];

            return (
              <article className="reservation-card" key={reservation.reservationId}>
                <strong>{reservation.movieTitle}</strong>
                <span>{formatDateTime(reservation.screeningStartTime)}</span>
                <span>{reservation.cinemaName} - {reservation.hallName}</span>
                <span>Seats: {reservation.seatLabels.join(', ')}</span>
                <span>Total: {reservation.totalAmount} RSD</span>
                <span>Status: {reservation.status}</span>
                <span>Expires: {formatDateTime(reservation.expiresAt)}</span>
                {latestPayment && (
                  <>
                    <span>Payment: {latestPayment.status}</span>
                    <span>Method: {latestPayment.method}</span>
                  </>
                )}
                {reservation.status === 'CONFIRMED' && (
                  <button type="button" onClick={() => viewTicket(reservation.reservationId)}>
                    View ticket
                  </button>
                )}
                {reservation.status === 'PENDING_PAYMENT' && (
                  <div className="payment-panel">
                    <strong>Payment options</strong>
                    <strong>Simulated card payment</strong>
                    <div className="actions">
                      <button
                        type="button"
                        onClick={() => payReservation(reservation.reservationId, true)}
                        disabled={payingReservationId === reservation.reservationId}
                      >
                        Simulate SUCCESS
                      </button>
                      <button
                        type="button"
                        onClick={() => payReservation(reservation.reservationId, false)}
                        disabled={payingReservationId === reservation.reservationId}
                      >
                        Simulate FAILURE
                      </button>
                      <button
                        type="button"
                        onClick={() => cancelReservation(reservation.reservationId)}
                        disabled={payingReservationId === reservation.reservationId}
                      >
                        Cancel reservation
                      </button>
                    </div>
                    <strong>MetaMask / ETH testnet</strong>
                    <button
                      type="button"
                      onClick={() => payWithMetaMask(reservation.reservationId)}
                      disabled={payingReservationId === reservation.reservationId}
                    >
                      Pay with MetaMask
                    </button>
                    {cryptoState && (
                      <div className="crypto-status">
                        <span>Status: {cryptoState.status}</span>
                        <span>{cryptoState.message}</span>
                        {cryptoState.walletAddress && (
                          <span>Connected wallet: {shortAddress(cryptoState.walletAddress)}</span>
                        )}
                        {cryptoState.prepare && (
                          <>
                            <span>Network: {cryptoState.prepare.network}</span>
                            <span>Amount RSD: {cryptoState.prepare.amountRsd}</span>
                            <span>Amount ETH: {cryptoState.prepare.cryptoAmount}</span>
                            <span>Merchant: {shortAddress(cryptoState.prepare.merchantAddress)}</span>
                          </>
                        )}
                        {cryptoState.transactionHash && (
                          <a
                            href={`https://sepolia.etherscan.io/tx/${cryptoState.transactionHash}`}
                            target="_blank"
                            rel="noreferrer"
                          >
                            Sepolia transaction
                          </a>
                        )}
                        {cryptoState.transactionHash && cryptoState.status === 'WAITING_CONFIRMATION' && (
                          <button
                            type="button"
                            onClick={() => verifyCryptoTransaction(reservation.reservationId)}
                            disabled={payingReservationId === reservation.reservationId}
                          >
                            Verify transaction
                          </button>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </article>
            );
          })
        )}
      </div>
      {message && <p className="status-message">{message}</p>}
      {error && <p className="error-message">{error}</p>}
      {selectedTicket && (
        <TicketView ticket={selectedTicket} qrUrl={ticketQrUrl} onClose={closeTicket} />
      )}
    </section>
  );
}

function TicketView({
  ticket,
  qrUrl,
  onClose,
}: {
  ticket: TicketResponse;
  qrUrl: string;
  onClose: () => void;
}) {
  return (
    <article className="ticket-panel">
      <div className="ticket-header">
        <div>
          <h3>{ticket.movieTitle}</h3>
          <span className={`status-pill ticket-status-${ticket.ticketStatus.toLowerCase()}`}>
            Ticket: {ticket.ticketStatus}
          </span>
        </div>
        <button type="button" onClick={onClose}>
          Close
        </button>
      </div>
      {qrUrl && <img className="ticket-qr" src={qrUrl} alt="Ticket QR code" />}
      <code>{ticket.ticketCode}</code>
      <span>{formatDateTime(ticket.screeningStartTime)}</span>
      <span>{ticket.cinemaName} - {ticket.hallName}</span>
      <span>Seats: {ticket.seats.join(', ')}</span>
      <span>Total: {ticket.totalAmount} RSD</span>
      <span>Reservation: #{ticket.reservationId}</span>
      <span>Created: {formatDateTime(ticket.createdAt)}</span>
      {ticket.usedAt && <span>Used: {formatDateTime(ticket.usedAt)}</span>}
    </article>
  );
}

function AdminReservationPanel({ refreshKey }: { refreshKey: number }) {
  const [reservations, setReservations] = useState<AdminReservationResponse[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    apiRequest<AdminReservationResponse[]>('/api/admin/reservations')
      .then((response) => {
        setReservations(response);
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [refreshKey]);

  return (
    <section className="data-section">
      <h2>All Reservations</h2>
      <div className="admin-reservation-list">
        {reservations.length === 0 ? (
          <p>No reservations yet.</p>
        ) : (
          reservations.map((reservation) => (
            <article className="reservation-card" key={reservation.reservationId}>
              <strong>{reservation.userEmail}</strong>
              <span>{reservation.movieTitle}</span>
              <span>{formatDateTime(reservation.startTime)}</span>
              <span>{reservation.cinemaName} - {reservation.hallName}</span>
              <span>Seats: {reservation.seats.join(', ')}</span>
              <span>Total: {reservation.totalAmount} RSD</span>
              <span className={`status-pill reservation-status-${reservation.reservationStatus.toLowerCase()}`}>
                Reservation: {reservation.reservationStatus}
              </span>
              <span className={`status-pill payment-status-${(reservation.latestPaymentStatus ?? 'none').toLowerCase()}`}>
                Payment: {reservation.latestPaymentStatus ?? 'No payment'}
              </span>
              <span>Method: {reservation.latestPaymentMethod ?? '-'}</span>
              <span>Reference: {reservation.latestPaymentReference ?? '-'}</span>
              {reservation.latestPaymentTransactionHash && (
                <a
                  href={`https://sepolia.etherscan.io/tx/${reservation.latestPaymentTransactionHash}`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Tx: {shortAddress(reservation.latestPaymentTransactionHash)}
                </a>
              )}
            </article>
          ))
        )}
      </div>
      {error && <p className="error-message">{error}</p>}
    </section>
  );
}

function AdminTicketValidationPanel() {
  const [ticket, setTicket] = useState<AdminTicketResponse | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  async function checkTicket(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const ticketCode = String(form.get('ticketCode') ?? '').trim();
    if (!ticketCode) {
      return;
    }

    try {
      const response = await apiRequest<AdminTicketResponse>(
        `/api/admin/tickets/${encodeURIComponent(ticketCode)}`,
      );
      setTicket(response);
      setMessage('');
      setError('');
    } catch (err) {
      setTicket(null);
      setMessage('');
      setError(getErrorMessage(err));
    }
  }

  async function validateEntry() {
    if (!ticket) {
      return;
    }

    try {
      const response = await apiRequest<TicketValidationResponse>(
        `/api/admin/tickets/${encodeURIComponent(ticket.ticketCode)}/validate`,
        { method: 'POST' },
      );
      setTicket(response.ticket);
      setMessage(response.message);
      setError('');
    } catch (err) {
      setMessage('');
      setError(getErrorMessage(err));
    }
  }

  return (
    <section className="data-section">
      <h2>Ticket Validation</h2>
      <form className="form-grid" onSubmit={checkTicket}>
        <label>
          Ticket code
          <input name="ticketCode" required />
        </label>
        <button type="submit">Check ticket</button>
      </form>
      {ticket && (
        <article className="ticket-check-panel">
          <strong>{ticket.movieTitle}</strong>
          <span className={`status-pill ticket-status-${ticket.ticketStatus.toLowerCase()}`}>
            Ticket: {ticket.ticketStatus}
          </span>
          <span className={`status-pill reservation-status-${ticket.reservationStatus.toLowerCase()}`}>
            Reservation: {ticket.reservationStatus}
          </span>
          <span>User: {ticket.userEmail}</span>
          <span>{formatDateTime(ticket.screeningStartTime)}</span>
          <span>{ticket.cinemaName} - {ticket.hallName}</span>
          <span>Seats: {ticket.seats.join(', ')}</span>
          <span>Reservation: #{ticket.reservationId}</span>
          <code>{ticket.ticketCode}</code>
          {ticket.usedAt && <span>Used: {formatDateTime(ticket.usedAt)}</span>}
          {ticket.ticketStatus === 'VALID' && ticket.reservationStatus === 'CONFIRMED' && (
            <button type="button" onClick={validateEntry}>
              Validate entry
            </button>
          )}
        </article>
      )}
      {message && <p className="status-message">{message}</p>}
      {error && <p className="error-message">{error}</p>}
    </section>
  );
}

function AdminMoviePanel({
  refreshKey,
  onChanged,
}: {
  refreshKey: number;
  onChanged: () => void;
}) {
  const [movies, setMovies] = useState<MovieResponse[]>([]);
  const [editingMovieId, setEditingMovieId] = useState('');
  const [error, setError] = useState('');

  const editingMovie = movies.find((movie) => movie.id === Number(editingMovieId));

  useEffect(() => {
    refreshMovies();
  }, [refreshKey]);

  async function refreshMovies() {
    try {
      const response = await apiRequest<MovieResponse[]>('/api/movies');
      setMovies(response);
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
      await apiRequest(path, {
        method,
        body: JSON.stringify(movieBodyFromForm(form)),
      });
      formElement.reset();
      setEditingMovieId('');
      await refreshMovies();
      onChanged();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function deleteMovie(movieId: number) {
    try {
      await apiRequest(`/api/admin/movies/${movieId}`, {
        method: 'DELETE',
      });
      if (editingMovieId === String(movieId)) {
        setEditingMovieId('');
      }
      await refreshMovies();
      onChanged();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <section className="data-section">
      <h2>Admin movie tools</h2>
      <form className="form-grid" onSubmit={submitMovie} key={editingMovie?.id ?? 'new-movie'}>
        <input name="movieId" type="hidden" defaultValue={editingMovie?.id ?? ''} />
        <label>
          Editing
          <select value={editingMovieId} onChange={(event) => setEditingMovieId(event.target.value)}>
            <option value="">New movie</option>
            {movies.map((movie) => (
              <option key={movie.id} value={movie.id}>
                {movie.title}
              </option>
            ))}
          </select>
        </label>
        <label>
          Title
          <input name="title" required defaultValue={editingMovie?.title ?? ''} />
        </label>
        <label>
          Description
          <textarea name="description" required defaultValue={editingMovie?.description ?? ''} />
        </label>
        <label>
          Genre
          <input name="genre" required defaultValue={editingMovie?.genre ?? ''} />
        </label>
        <label>
          Duration
          <input
            name="durationMinutes"
            type="number"
            min="1"
            required
            defaultValue={editingMovie?.durationMinutes ?? ''}
          />
        </label>
        <label>
          Age rating
          <input name="ageRating" defaultValue={editingMovie?.ageRating ?? ''} />
        </label>
        <label>
          Director
          <input name="director" defaultValue={editingMovie?.director ?? ''} />
        </label>
        <label>
          Release date
          <input name="releaseDate" type="date" defaultValue={editingMovie?.releaseDate ?? ''} />
        </label>
        <label>
          Poster URL
          <input name="posterUrl" type="url" defaultValue={editingMovie?.posterUrl ?? ''} />
        </label>
        <label>
          Trailer URL
          <input name="trailerUrl" type="url" defaultValue={editingMovie?.trailerUrl ?? ''} />
        </label>
        <div className="actions">
          <button type="submit">{editingMovie ? 'Update movie' : 'Add movie'}</button>
          {editingMovie && (
            <button type="button" onClick={() => deleteMovie(editingMovie.id)}>
              Delete movie
            </button>
          )}
        </div>
      </form>
      {error && <p className="error-message">{error}</p>}
    </section>
  );
}

function AdminScreeningPanel({
  refreshKey,
  onChanged,
}: {
  refreshKey: number;
  onChanged: () => void;
}) {
  const [movies, setMovies] = useState<MovieResponse[]>([]);
  const [cities, setCities] = useState<CityResponse[]>([]);
  const [cinemas, setCinemas] = useState<CinemaResponse[]>([]);
  const [halls, setHalls] = useState<HallResponse[]>([]);
  const [screenings, setScreenings] = useState<ScreeningResponse[]>([]);
  const [editingScreeningId, setEditingScreeningId] = useState('');
  const [selectedCityId, setSelectedCityId] = useState('');
  const [selectedCinemaId, setSelectedCinemaId] = useState('');
  const [error, setError] = useState('');

  const editingScreening = screenings.find((screening) => screening.id === Number(editingScreeningId));
  const availableCinemas = selectedCityId
    ? cinemas.filter((cinema) => cinema.city.id === Number(selectedCityId))
    : cinemas;
  const availableHalls = selectedCinemaId
    ? halls.filter((hall) => hall.cinemaId === Number(selectedCinemaId))
    : halls;

  useEffect(() => {
    refreshAdminScreeningData();
  }, [refreshKey]);

  useEffect(() => {
    if (!editingScreening) {
      return;
    }
    setSelectedCityId(String(editingScreening.cityId));
    setSelectedCinemaId(String(editingScreening.cinemaId));
  }, [editingScreening]);

  async function refreshAdminScreeningData() {
    try {
      const [nextMovies, nextCities, nextCinemas, nextScreenings] = await Promise.all([
        apiRequest<MovieResponse[]>('/api/movies'),
        apiRequest<CityResponse[]>('/api/cities'),
        apiRequest<CinemaResponse[]>('/api/cinemas'),
        apiRequest<ScreeningResponse[]>('/api/screenings'),
      ]);
      const hallGroups = await Promise.all(
        nextCinemas.map((cinema) => apiRequest<HallResponse[]>(`/api/cinemas/${cinema.id}/halls`)),
      );
      setMovies(nextMovies);
      setCities(nextCities);
      setCinemas(nextCinemas);
      setHalls(hallGroups.flat());
      setScreenings(nextScreenings);
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function submitScreening(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const screeningId = String(form.get('screeningId') ?? '');
    const path = screeningId ? `/api/admin/screenings/${screeningId}` : '/api/admin/screenings';
    const method = screeningId ? 'PUT' : 'POST';

    try {
      await apiRequest(path, {
        method,
        body: JSON.stringify(screeningBodyFromForm(form)),
      });
      formElement.reset();
      setEditingScreeningId('');
      setSelectedCityId('');
      setSelectedCinemaId('');
      await refreshAdminScreeningData();
      onChanged();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function deleteScreening(screeningId: number) {
    try {
      await apiRequest(`/api/admin/screenings/${screeningId}`, {
        method: 'DELETE',
      });
      if (editingScreeningId === String(screeningId)) {
        setEditingScreeningId('');
      }
      await refreshAdminScreeningData();
      onChanged();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  function changeEditingScreening(screeningId: string) {
    setEditingScreeningId(screeningId);
    if (!screeningId) {
      setSelectedCityId('');
      setSelectedCinemaId('');
    }
  }

  return (
    <section className="data-section">
      <h2>Admin screening tools</h2>
      <form
        className="form-grid"
        onSubmit={submitScreening}
        key={editingScreening?.id ?? 'new-screening'}
      >
        <input name="screeningId" type="hidden" defaultValue={editingScreening?.id ?? ''} />
        <label>
          Editing
          <select value={editingScreeningId} onChange={(event) => changeEditingScreening(event.target.value)}>
            <option value="">New screening</option>
            {screenings.map((screening) => (
              <option key={screening.id} value={screening.id}>
                {screening.movieTitle} - {formatDate(screening.startTime)} {formatTime(screening.startTime)}
              </option>
            ))}
          </select>
        </label>
        <label>
          Movie
          <select name="movieId" required defaultValue={editingScreening?.movieId ?? ''}>
            <option value="">Select movie</option>
            {movies.map((movie) => (
              <option key={movie.id} value={movie.id}>
                {movie.title}
              </option>
            ))}
          </select>
        </label>
        <label>
          City
          <select value={selectedCityId} onChange={(event) => setSelectedCityId(event.target.value)}>
            <option value="">Select city</option>
            {cities.map((city) => (
              <option key={city.id} value={city.id}>
                {city.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Cinema
          <select value={selectedCinemaId} onChange={(event) => setSelectedCinemaId(event.target.value)}>
            <option value="">Select cinema</option>
            {availableCinemas.map((cinema) => (
              <option key={cinema.id} value={cinema.id}>
                {cinema.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Hall
          <select name="hallId" required defaultValue={editingScreening?.hallId ?? ''}>
            <option value="">Select hall</option>
            {availableHalls.map((hall) => (
              <option key={hall.id} value={hall.id}>
                {hall.cinemaName} - {hall.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Date
          <input
            name="date"
            type="date"
            required
            defaultValue={editingScreening?.startTime.slice(0, 10) ?? ''}
          />
        </label>
        <label>
          Time
          <input
            name="time"
            type="time"
            required
            defaultValue={editingScreening?.startTime.slice(11, 16) ?? ''}
          />
        </label>
        <label>
          Ticket price
          <input
            name="ticketPrice"
            type="number"
            min="1"
            step="0.01"
            required
            defaultValue={editingScreening?.ticketPrice ?? ''}
          />
        </label>
        <div className="actions">
          <button type="submit">{editingScreening ? 'Update screening' : 'Add screening'}</button>
          {editingScreening && (
            <button type="button" onClick={() => deleteScreening(editingScreening.id)}>
              Delete screening
            </button>
          )}
        </div>
      </form>
      {error && <p className="error-message">{error}</p>}
    </section>
  );
}

function CatalogBrowser({ refreshKey }: { refreshKey: number }) {
  const [cities, setCities] = useState<CityResponse[]>([]);
  const [cinemas, setCinemas] = useState<CinemaResponse[]>([]);
  const [halls, setHalls] = useState<HallResponse[]>([]);
  const [seats, setSeats] = useState<SeatResponse[]>([]);
  const [selectedCityId, setSelectedCityId] = useState('');
  const [selectedCinemaId, setSelectedCinemaId] = useState('');
  const [selectedHallId, setSelectedHallId] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    apiRequest<CityResponse[]>('/api/cities')
      .then((response) => {
        setCities(response);
        if (selectedCityId && !response.some((city) => city.id === Number(selectedCityId))) {
          setSelectedCityId('');
        }
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [refreshKey, selectedCityId]);

  useEffect(() => {
    if (!selectedCityId) {
      setCinemas([]);
      setSelectedCinemaId('');
      return;
    }

    apiRequest<CinemaResponse[]>(`/api/cities/${selectedCityId}/cinemas`)
      .then((response) => {
        setCinemas(response);
        if (selectedCinemaId && !response.some((cinema) => cinema.id === Number(selectedCinemaId))) {
          setSelectedCinemaId('');
        }
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [refreshKey, selectedCityId, selectedCinemaId]);

  useEffect(() => {
    if (!selectedCinemaId) {
      setHalls([]);
      setSelectedHallId('');
      return;
    }

    apiRequest<HallResponse[]>(`/api/cinemas/${selectedCinemaId}/halls`)
      .then((response) => {
        setHalls(response);
        if (selectedHallId && !response.some((hall) => hall.id === Number(selectedHallId))) {
          setSelectedHallId('');
        }
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [refreshKey, selectedCinemaId, selectedHallId]);

  useEffect(() => {
    if (!selectedHallId) {
      setSeats([]);
      return;
    }

    apiRequest<SeatResponse[]>(`/api/halls/${selectedHallId}/seats`)
      .then(setSeats)
      .catch((err) => setError(getErrorMessage(err)));
  }, [refreshKey, selectedHallId]);

  return (
    <section className="data-section">
      <h2>Public cinema structure</h2>
      <div className="form-grid">
        <label>
          City
          <select value={selectedCityId} onChange={(event) => setSelectedCityId(event.target.value)}>
            <option value="">Select city</option>
            {cities.map((city) => (
              <option key={city.id} value={city.id}>
                {city.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Cinema
          <select
            value={selectedCinemaId}
            onChange={(event) => setSelectedCinemaId(event.target.value)}
            disabled={!selectedCityId}
          >
            <option value="">Select cinema</option>
            {cinemas.map((cinema) => (
              <option key={cinema.id} value={cinema.id}>
                {cinema.name} - {cinema.address}
              </option>
            ))}
          </select>
        </label>
        <label>
          Hall
          <select
            value={selectedHallId}
            onChange={(event) => setSelectedHallId(event.target.value)}
            disabled={!selectedCinemaId}
          >
            <option value="">Select hall</option>
            {halls.map((hall) => (
              <option key={hall.id} value={hall.id}>
                {hall.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      {selectedHallId && (
        <div className="seat-grid" aria-label="Hall seats">
          {seats.map((seat) => (
            <span key={seat.id}>{seat.rowLabel}{seat.seatNumber}</span>
          ))}
        </div>
      )}

      {error && <p className="error-message">{error}</p>}
    </section>
  );
}

function AdminStructurePanel({
  refreshKey,
  onChanged,
}: {
  refreshKey: number;
  onChanged: () => void;
}) {
  const [cities, setCities] = useState<CityResponse[]>([]);
  const [cinemas, setCinemas] = useState<CinemaResponse[]>([]);
  const [halls, setHalls] = useState<HallResponse[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    refreshAdminData();
  }, [refreshKey]);

  async function refreshAdminData() {
    try {
      const nextCities = await apiRequest<CityResponse[]>('/api/cities');
      const nextCinemas = await apiRequest<CinemaResponse[]>('/api/cinemas');
      const hallGroups = await Promise.all(
        nextCinemas.map((cinema) => apiRequest<HallResponse[]>(`/api/cinemas/${cinema.id}/halls`)),
      );
      setCities(nextCities);
      setCinemas(nextCinemas);
      setHalls(hallGroups.flat());
      setError('');
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  async function submitCity(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await submitAdminRequest('/api/admin/cities', {
      name: form.get('name'),
    }, formElement);
  }

  async function submitCinema(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await submitAdminRequest('/api/admin/cinemas', {
      name: form.get('name'),
      address: form.get('address'),
      cityId: Number(form.get('cityId')),
    }, formElement);
  }

  async function submitHall(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    await submitAdminRequest('/api/admin/halls', {
      name: form.get('name'),
      cinemaId: Number(form.get('cinemaId')),
    }, formElement);
  }

  async function submitSeatGeneration(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const hallId = Number(form.get('hallId'));
    await submitAdminRequest(`/api/admin/halls/${hallId}/seats/generate`, {
      rows: Number(form.get('rows')),
      seatsPerRow: Number(form.get('seatsPerRow')),
    }, formElement);
  }

  async function submitAdminRequest(path: string, body: object, formElement: HTMLFormElement) {
    try {
      await apiRequest(path, {
        method: 'POST',
        body: JSON.stringify(body),
      });
      formElement.reset();
      await refreshAdminData();
      onChanged();
    } catch (err) {
      setError(getErrorMessage(err));
    }
  }

  return (
    <section className="data-section">
      <h2>Admin structure tools</h2>
      <div className="admin-grid">
        <form className="form-grid" onSubmit={submitCity}>
          <h3>Add city</h3>
          <label>
            Name
            <input name="name" required />
          </label>
          <button type="submit">Add city</button>
        </form>

        <form className="form-grid" onSubmit={submitCinema}>
          <h3>Add cinema</h3>
          <label>
            Name
            <input name="name" required />
          </label>
          <label>
            Address
            <input name="address" required />
          </label>
          <label>
            City
            <select name="cityId" required>
              <option value="">Select city</option>
              {cities.map((city) => (
                <option key={city.id} value={city.id}>
                  {city.name}
                </option>
              ))}
            </select>
          </label>
          <button type="submit">Add cinema</button>
        </form>

        <form className="form-grid" onSubmit={submitHall}>
          <h3>Add hall</h3>
          <label>
            Name
            <input name="name" required />
          </label>
          <label>
            Cinema
            <select name="cinemaId" required>
              <option value="">Select cinema</option>
              {cinemas.map((cinema) => (
                <option key={cinema.id} value={cinema.id}>
                  {cinema.name}
                </option>
              ))}
            </select>
          </label>
          <button type="submit">Add hall</button>
        </form>

        <form className="form-grid" onSubmit={submitSeatGeneration}>
          <h3>Generate seats</h3>
          <label>
            Hall
            <select name="hallId" required>
              <option value="">Select hall</option>
              {halls.map((hall) => (
                <option key={hall.id} value={hall.id}>
                  {hall.cinemaName} - {hall.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Rows
            <input name="rows" type="number" min="1" required />
          </label>
          <label>
            Seats per row
            <input name="seatsPerRow" type="number" min="1" required />
          </label>
          <button type="submit">Generate seats</button>
        </form>
      </div>
      {error && <p className="error-message">{error}</p>}
    </section>
  );
}

function getPageFromPath(): Page {
  if (window.location.pathname === '/register') {
    return 'register';
  }
  if (window.location.pathname === '/login') {
    return 'login';
  }
  return 'home';
}

function movieBodyFromForm(form: FormData) {
  return {
    title: form.get('title'),
    description: form.get('description'),
    genre: form.get('genre'),
    durationMinutes: Number(form.get('durationMinutes')),
    ageRating: optionalFormValue(form.get('ageRating')),
    director: optionalFormValue(form.get('director')),
    releaseDate: optionalFormValue(form.get('releaseDate')),
    posterUrl: optionalFormValue(form.get('posterUrl')),
    trailerUrl: optionalFormValue(form.get('trailerUrl')),
  };
}

function screeningBodyFromForm(form: FormData) {
  return {
    movieId: Number(form.get('movieId')),
    hallId: Number(form.get('hallId')),
    startTime: `${form.get('date')}T${form.get('time')}:00`,
    ticketPrice: Number(form.get('ticketPrice')),
  };
}

function optionalFormValue(value: FormDataEntryValue | null) {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('sr-RS', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date(value));
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('sr-RS', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat('sr-RS', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}

function decimalEthToWeiHex(value: number | string) {
  const [wholePart, fractionPart = ''] = String(value).split('.');
  const paddedFraction = `${fractionPart}000000000000000000`.slice(0, 18);
  const wei = BigInt(wholePart || '0') * 1000000000000000000n + BigInt(paddedFraction || '0');
  return `0x${wei.toString(16)}`;
}

function shortAddress(value: string) {
  if (value.length <= 14) {
    return value;
  }
  return `${value.slice(0, 8)}...${value.slice(-6)}`;
}

function blobToDataUrl(blob: Blob) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(blob);
  });
}

function getErrorMessage(error: unknown) {
  if (typeof error === 'object' && error && 'message' in error) {
    return String(error.message);
  }
  return 'Unexpected error';
}

export default App;
