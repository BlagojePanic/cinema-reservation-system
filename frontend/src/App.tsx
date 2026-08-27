import { useEffect, useState } from 'react';

type HealthState = 'checking' | 'up' | 'down';

function App() {
  const [health, setHealth] = useState<HealthState>('checking');

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

  const message =
    health === 'checking'
      ? 'Checking backend status...'
      : health === 'up'
        ? 'Backend is available.'
        : 'Backend is not available.';

  return (
    <main className="app-shell">
      <section className="status-panel">
        <p className="eyebrow">CryptoCinema</p>
        <h1>Project initialization</h1>
        <p>{message}</p>
      </section>
    </main>
  );
}

export default App;
