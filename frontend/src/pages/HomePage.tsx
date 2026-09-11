import { useEffect, useMemo, useState } from 'react';
import { apiRequest, CinemaResponse, CityResponse, MovieResponse, ScreeningResponse } from '../api';
import { MovieCard } from '../components/MovieCard';

type HomePageProps = {
  onNavigate: (path: string) => void;
};

export function HomePage({ onNavigate }: HomePageProps) {
  const [movies, setMovies] = useState<MovieResponse[]>([]);
  const [screenings, setScreenings] = useState<ScreeningResponse[]>([]);
  const [cities, setCities] = useState<CityResponse[]>([]);
  const [cinemas, setCinemas] = useState<CinemaResponse[]>([]);
  const [cityId, setCityId] = useState('');
  const [cinemaId, setCinemaId] = useState('');
  const [date, setDate] = useState('');
  const [genre, setGenre] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      apiRequest<MovieResponse[]>('/api/movies'),
      apiRequest<ScreeningResponse[]>('/api/screenings'),
      apiRequest<CityResponse[]>('/api/cities'),
      apiRequest<CinemaResponse[]>('/api/cinemas'),
    ])
      .then(([nextMovies, nextScreenings, nextCities, nextCinemas]) => {
        setMovies(nextMovies);
        setScreenings(nextScreenings);
        setCities(nextCities);
        setCinemas(nextCinemas);
        setError('');
      })
      .catch(() => setError('Unable to load movies. Please try again.'))
      .finally(() => setLoading(false));
  }, []);

  const availableCinemas = cityId ? cinemas.filter((cinema) => cinema.city.id === Number(cityId)) : cinemas;
  const genres = useMemo(() => Array.from(new Set(movies.map((movie) => movie.genre))).sort(), [movies]);
  const hasScheduleFilter = Boolean(cityId || cinemaId || date);

  const filteredMovies = movies.filter((movie) => {
    if (genre && movie.genre !== genre) {
      return false;
    }

    if (!hasScheduleFilter) {
      return true;
    }

    return screenings.some((screening) => (
      screening.movieId === movie.id
      && (!cityId || screening.cityId === Number(cityId))
      && (!cinemaId || screening.cinemaId === Number(cinemaId))
      && (!date || screening.startTime.slice(0, 10) === date)
    ));
  });

  function openMovie(movieId: number) {
    const params = new URLSearchParams();
    if (cityId) params.set('cityId', cityId);
    if (cinemaId) params.set('cinemaId', cinemaId);
    if (date) params.set('date', date);
    const query = params.toString();
    onNavigate(`/movies/${movieId}${query ? `?${query}` : ''}`);
  }

  return (
    <main className="page">
      <section className="catalog-header">
        <p className="eyebrow">Now Showing</p>
        <h1>Movies</h1>
      </section>

      <section className="filter-panel" aria-label="Movie filters">
        <label>City
          <select value={cityId} onChange={(event) => {
            setCityId(event.target.value);
            setCinemaId('');
          }}>
            <option value="">All cities</option>
            {cities.map((city) => <option key={city.id} value={city.id}>{city.name}</option>)}
          </select>
        </label>
        <label>Cinema
          <select value={cinemaId} onChange={(event) => setCinemaId(event.target.value)}>
            <option value="">All cinemas</option>
            {availableCinemas.map((cinema) => <option key={cinema.id} value={cinema.id}>{cinema.name}</option>)}
          </select>
        </label>
        <label>Date
          <input type="date" value={date} onChange={(event) => setDate(event.target.value)} />
        </label>
        <label>Genre
          <select value={genre} onChange={(event) => setGenre(event.target.value)}>
            <option value="">All genres</option>
            {genres.map((nextGenre) => <option key={nextGenre} value={nextGenre}>{nextGenre}</option>)}
          </select>
        </label>
      </section>

      {loading && <p className="page-message">Loading movies...</p>}
      {error && <p className="error-message page-message">{error}</p>}
      {!loading && !error && filteredMovies.length === 0 && (
        <p className="empty-state">No movies match the selected filters.</p>
      )}
      {!loading && !error && filteredMovies.length > 0 && (
        <div className="movie-grid">
          {filteredMovies.map((movie) => (
            <MovieCard key={movie.id} movie={movie} onOpen={openMovie} />
          ))}
        </div>
      )}
    </main>
  );
}
