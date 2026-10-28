const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api'

async function request(path, { token, ...options } = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options?.headers,
    },
  })
  const payload = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) throw new Error(payload?.detail || payload?.message || 'Something went wrong. Please try again.')
  return payload
}

export const api = {
  login: (email, password) => request('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  register: (displayName, email, password) => request('/auth/register', { method: 'POST', body: JSON.stringify({ displayName, email, password }) }),
  me: (token) => request('/me', { token }),
  exercises: (token) => request('/exercises', { token }),
  addExercise: (token, exercise) => request('/exercises', { token, method: 'POST', body: JSON.stringify(exercise) }),
  workouts: (token) => request('/workouts', { token }),
  startWorkout: (token) => request('/workouts', { token, method: 'POST' }),
  addSet: (token, workoutId, set) => request(`/workouts/${workoutId}/sets`, { token, method: 'POST', body: JSON.stringify(set) }),
  finishWorkout: (token, workoutId) => request(`/workouts/${workoutId}/finish`, { token, method: 'POST' }),
}

export function formatDuration(seconds = 0) {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remaining = seconds % 60
  return hours ? `${hours}:${String(minutes).padStart(2, '0')}:${String(remaining).padStart(2, '0')}` : `${minutes}:${String(remaining).padStart(2, '0')}`
}

export function formatDate(value) {
  return new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value))
}
