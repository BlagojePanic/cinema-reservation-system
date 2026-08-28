const TOKEN_KEY = 'cryptocinema_token';

export type UserRole = 'USER' | 'ADMIN';

export type UserResponse = {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: UserRole;
};

export type AuthResponse = {
  token: string;
  tokenType: 'Bearer';
  user: UserResponse;
};

export type CityResponse = {
  id: number;
  name: string;
};

export type CinemaResponse = {
  id: number;
  name: string;
  address: string;
  city: CityResponse;
};

export type HallResponse = {
  id: number;
  name: string;
  cinemaId: number;
  cinemaName: string;
};

export type SeatResponse = {
  id: number;
  rowLabel: string;
  seatNumber: number;
  hallId: number;
};

export type MovieResponse = {
  id: number;
  title: string;
  description: string;
  genre: string;
  durationMinutes: number;
  ageRating: string | null;
  director: string | null;
  releaseDate: string | null;
  posterUrl: string | null;
  trailerUrl: string | null;
};

export type ApiError = {
  message: string;
  status?: number;
};

export function saveToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function apiRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  const token = getToken();

  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json');
  }

  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(path, {
    ...options,
    headers,
  });

  if (!response.ok) {
    let message = `Request failed with status ${response.status}`;
    try {
      const body = (await response.json()) as { message?: string };
      message = body.message ?? message;
    } catch {
      // Keep the default message when the response has no JSON body.
    }
    throw { message, status: response.status } satisfies ApiError;
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  if (!text) {
    return undefined as T;
  }

  return JSON.parse(text) as T;
}
