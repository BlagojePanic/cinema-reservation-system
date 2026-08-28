import { FormEvent, useEffect, useState } from 'react';
import {
  apiRequest,
  AuthResponse,
  CinemaResponse,
  CityResponse,
  clearToken,
  getToken,
  HallResponse,
  MovieResponse,
  saveToken,
  SeatResponse,
  UserResponse,
} from './api';

type HealthState = 'checking' | 'up' | 'down';
type Page = 'home' | 'register' | 'login';

function App() {
  const [health, setHealth] = useState<HealthState>('checking');
  const [page, setPage] = useState<Page>(() => getPageFromPath());
  const [currentUser, setCurrentUser] = useState<UserResponse | null>(null);
  const [statusMessage, setStatusMessage] = useState('');
  const [structureRefreshKey, setStructureRefreshKey] = useState(0);
  const [movieRefreshKey, setMovieRefreshKey] = useState(0);

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
            <MovieBrowser refreshKey={movieRefreshKey} />
            {currentUser?.role === 'ADMIN' && (
              <AdminMoviePanel
                refreshKey={movieRefreshKey}
                onChanged={() => {
                  refreshMovieData();
                  setStatusMessage('Movie catalog updated.');
                }}
              />
            )}
            <CatalogBrowser refreshKey={structureRefreshKey} />
            {currentUser?.role === 'ADMIN' && (
              <AdminStructurePanel
                refreshKey={structureRefreshKey}
                onChanged={() => {
                  refreshStructureData();
                  setStatusMessage('Cinema structure updated.');
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

function MovieBrowser({ refreshKey }: { refreshKey: number }) {
  const [movies, setMovies] = useState<MovieResponse[]>([]);
  const [selectedMovie, setSelectedMovie] = useState<MovieResponse | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    apiRequest<MovieResponse[]>('/api/movies')
      .then((response) => {
        setMovies(response);
        setSelectedMovie((current) =>
          current ? response.find((movie) => movie.id === current.id) ?? null : null,
        );
        setError('');
      })
      .catch((err) => setError(getErrorMessage(err)));
  }, [refreshKey]);

  async function openDetails(movieId: number) {
    try {
      const movie = await apiRequest<MovieResponse>(`/api/movies/${movieId}`);
      setSelectedMovie(movie);
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
          </div>
        </article>
      )}

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

function optionalFormValue(value: FormDataEntryValue | null) {
  if (typeof value !== 'string') {
    return null;
  }
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function getErrorMessage(error: unknown) {
  if (typeof error === 'object' && error && 'message' in error) {
    return String(error.message);
  }
  return 'Unexpected error';
}

export default App;
