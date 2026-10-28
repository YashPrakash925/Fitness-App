import { useCallback, useEffect, useMemo, useState } from 'react'
import { Activity, ArrowLeft, ArrowRight, BarChart3, Check, Clock3, Dumbbell, LogOut, Plus, Search, Sparkles, Target, X } from 'lucide-react'
import { api, formatDate, formatDuration } from './api.js'

const emptyCredentials = { displayName: '', email: '', password: '' }

function App() {
  const [session, setSession] = useState(() => {
    try { return JSON.parse(localStorage.getItem('fitness-session') || 'null') } catch { return null }
  })
  const [user, setUser] = useState(session?.user || null)
  const [page, setPage] = useState('home')
  const [workouts, setWorkouts] = useState([])
  const [exercises, setExercises] = useState([])
  const [activeWorkout, setActiveWorkout] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const signOut = () => {
    localStorage.removeItem('fitness-session')
    setSession(null); setUser(null); setWorkouts([]); setActiveWorkout(null); setPage('home')
  }

  const refresh = useCallback(async (token = session?.token) => {
    if (!token) return
    const [profile, history, catalog] = await Promise.all([api.me(token), api.workouts(token), api.exercises(token)])
    setUser(profile); setWorkouts(history); setExercises(catalog)
    localStorage.setItem('fitness-session', JSON.stringify({ token, user: profile }))
    setSession({ token, user: profile })
    return history
  }, [session?.token])

  useEffect(() => {
    if (!session?.token) return
    refresh().catch(() => signOut())
  }, []) // validate a stored login once when the app opens

  useEffect(() => {
    if (!session?.token) return
    const current = workouts.find((workout) => !workout.finishedAt)
    if (current && !activeWorkout) setActiveWorkout(current)
  }, [workouts, session?.token])

  const run = async (action) => {
    setBusy(true); setError('')
    try { return await action() } catch (e) { setError(e.message); return null } finally { setBusy(false) }
  }

  const authenticate = async (mode, credentials) => run(async () => {
    const result = mode === 'register'
      ? await api.register(credentials.displayName, credentials.email, credentials.password)
      : await api.login(credentials.email, credentials.password)
    localStorage.setItem('fitness-session', JSON.stringify(result))
    setSession(result); setUser(result.user)
    const history = await refresh(result.token)
    setPage('home')
    const current = history?.find((workout) => !workout.finishedAt)
    if (current) { setActiveWorkout(current); setPage('workout') }
  })

  const startWorkout = async () => run(async () => {
    const workout = await api.startWorkout(session.token)
    setWorkouts((previous) => [workout, ...previous]); setActiveWorkout(workout); setPage('workout')
  })
  const continueWorkout = () => setPage('workout')

  const addSet = async (exercise, reps, weightKg) => run(async () => {
    const updated = await api.addSet(session.token, activeWorkout.id, { exerciseId: exercise.id, reps: Number(reps), weightKg: Number(weightKg) })
    setActiveWorkout(updated); setWorkouts((previous) => previous.map((item) => item.id === updated.id ? updated : item))
  })

  const finishWorkout = async () => run(async () => {
    const finished = await api.finishWorkout(session.token, activeWorkout.id)
    setActiveWorkout(null); setWorkouts((previous) => previous.map((item) => item.id === finished.id ? finished : item))
    setPage('home')
  })

  const createExercise = async (exercise) => run(async () => {
    const created = await api.addExercise(session.token, exercise)
    setExercises((previous) => [...previous, created].sort((a, b) => a.name.localeCompare(b.name)))
    return created
  })

  if (!session?.token) return <AuthScreen onAuthenticate={authenticate} busy={busy} error={error} />

  const completed = workouts.filter((item) => item.finishedAt)
  const totalVolume = completed.reduce((sum, item) => sum + item.totalVolumeKg, 0)
  const totalTime = completed.reduce((sum, item) => sum + item.durationSeconds, 0)

  return <div className="app-shell">
    <aside className="sidebar">
      <a className="brand" href="#home" onClick={(e) => { e.preventDefault(); setPage('home') }}><span className="brand-mark"><Activity size={19}/></span><span>Fitness Tracker</span></a>
      <div className="side-label">WORKSPACE</div>
      <button className={`nav-item ${page === 'home' ? 'selected' : ''}`} onClick={() => setPage('home')}><BarChart3 size={18}/> Overview</button>
      <button className={`nav-item ${page === 'history' ? 'selected' : ''}`} onClick={() => setPage('history')}><Clock3 size={18}/> Workout history</button>
      <div className="sidebar-bottom"><div className="profile-chip"><div className="avatar">{(user?.displayName || 'U').slice(0, 1).toUpperCase()}</div><div className="profile-text"><strong>{user?.displayName}</strong><span>{user?.email}</span></div><button aria-label="Sign out" className="icon-button" onClick={signOut}><LogOut size={17}/></button></div></div>
    </aside>
    <main className="main-content">
      <header className="mobile-header"><a className="brand" href="#home" onClick={(e) => { e.preventDefault(); setPage('home') }}><span className="brand-mark"><Activity size={19}/></span><span>Fitness Tracker</span></a><button className="icon-button" aria-label="Sign out" onClick={signOut}><LogOut size={17}/></button></header>
      <div className="content-wrap">
        {error && <div className="alert"><span>{error}</span><button onClick={() => setError('')} aria-label="Dismiss"><X size={16}/></button></div>}
        {page === 'workout' && activeWorkout
          ? <WorkoutScreen workout={activeWorkout} exercises={exercises} onAddSet={addSet} onCreateExercise={createExercise} onFinish={finishWorkout} onBack={() => setPage('home')} busy={busy}/>
          : page === 'history'
            ? <HistoryScreen workouts={completed}/>
            : <Dashboard user={user} workouts={completed} totalVolume={totalVolume} totalTime={totalTime} onStart={activeWorkout ? continueWorkout : startWorkout} hasActive={Boolean(activeWorkout)} onHistory={() => setPage('history')} busy={busy}/>}
      </div>
    </main>
  </div>
}

function AuthScreen({ onAuthenticate, busy, error }) {
  const [mode, setMode] = useState('login')
  const [values, setValues] = useState(emptyCredentials)
  const isRegister = mode === 'register'
  const update = (key) => (event) => setValues((current) => ({ ...current, [key]: event.target.value }))
  const submit = (event) => { event.preventDefault(); onAuthenticate(mode, values) }
  return <div className="auth-layout">
    <section className="auth-story"><a className="brand light-brand" href="#"><span className="brand-mark"><Activity size={19}/></span><span>Fitness Tracker</span></a>
      <div className="story-copy"><div className="eyebrow"><span className="eyebrow-line"/> YOUR TRAINING, IN FOCUS</div><h1>Show up.<br/><span>Get stronger.</span></h1><p>A clearer picture of your training starts with one good session.</p><div className="story-stat"><div className="story-stat-icon"><Target size={18}/></div><div><strong>Progress you can measure</strong><span>Every rep. Every session. All yours.</span></div></div></div>
      <div className="story-footer">BUILT FOR YOUR NEXT PERSONAL BEST</div>
    </section>
    <section className="auth-panel"><div className="auth-card"><div className="auth-heading"><span className="section-kicker">{isRegister ? 'GET STARTED' : 'WELCOME BACK'}</span><h2>{isRegister ? 'Create your account' : 'Sign in to your account'}</h2><p>{isRegister ? 'Your training history, all in one place.' : 'Pick up where you left off.'}</p></div>
      {error && <div className="auth-error">{error}</div>}
      <form className="auth-form" onSubmit={submit}>
        {isRegister && <label>Your name<input autoComplete="name" placeholder="Alex Morgan" value={values.displayName} onChange={update('displayName')} required maxLength="80"/></label>}
        <label>Email address<input type="email" autoComplete="email" placeholder="you@example.com" value={values.email} onChange={update('email')} required maxLength="254"/></label>
        <label>Password<input type="password" autoComplete={isRegister ? 'new-password' : 'current-password'} placeholder={isRegister ? 'At least 8 characters' : 'Enter your password'} value={values.password} onChange={update('password')} required minLength={isRegister ? 8 : undefined} maxLength="72"/></label>
        <button className="primary-button auth-submit" disabled={busy}>{busy ? 'Please wait…' : isRegister ? 'Create account' : 'Sign in'} <ArrowRight size={17}/></button>
      </form>
      <div className="auth-switch">{isRegister ? 'Already have an account?' : 'New to Fitness Tracker?'} <button onClick={() => { setMode(isRegister ? 'login' : 'register'); setValues(emptyCredentials) }}>{isRegister ? 'Sign in' : 'Create account'}</button></div>
    </div><div className="auth-legal">By continuing, your training data stays private to your account.</div></section>
  </div>
}

function Dashboard({ user, workouts, totalVolume, totalTime, onStart, onHistory, hasActive, busy }) {
  const latest = workouts[0]
  return <>
    <div className="page-heading"><div><span className="section-kicker">YOUR TRAINING SPACE</span><h1>Good to see you, {user?.displayName?.split(' ')[0] || 'there'}<span className="heading-period">.</span></h1><p>Small steps add up. Let's make today's count.</p></div><button onClick={onStart} className="primary-button start-button" disabled={busy}>{hasActive ? <ArrowRight size={18} /> : <Plus size={18} />}{busy ? 'Starting…' : hasActive ? 'Resume workout' : 'Start a workout'}</button></div>
    <div className="stats-grid">
      <StatCard label="TOTAL WORKOUTS" value={String(workouts.length).padStart(2, '0')} icon={<Dumbbell size={17}/>} foot="Completed sessions"/>
      <StatCard label="TOTAL VOLUME" value={`${Math.round(totalVolume).toLocaleString()} kg`} icon={<BarChart3 size={17}/>} foot="Reps × weight"/>
      <StatCard label="TIME TRAINED" value={formatDuration(totalTime)} icon={<Clock3 size={17}/>} foot="Across all sessions"/>
    </div>
    <div className="section-heading"><div><span className="section-kicker">RECENT ACTIVITY</span><h2>Your recent workouts</h2></div><button className="text-button" onClick={onHistory}>View history <ArrowRight size={15}/></button></div>
    {latest ? <div className="recent-card"><div className="recent-date-icon"><Dumbbell size={19}/></div><div className="recent-main"><strong>Strength training</strong><span>{formatDate(latest.startedAt)} · {latest.sets.length} sets logged</span></div><div className="recent-metric"><span className="metric-label">DURATION</span><strong>{formatDuration(latest.durationSeconds)}</strong></div><div className="recent-metric volume-metric"><span className="metric-label">VOLUME</span><strong>{Math.round(latest.totalVolumeKg).toLocaleString()} <small>kg</small></strong></div><span className="complete-badge"><Check size={13}/> Complete</span></div>
      : <div className="empty-card"><div className="empty-icon"><Sparkles size={20}/></div><div><strong>Your first workout is waiting.</strong><p>Start a session and log the sets as you go. Your progress will show up here.</p></div><button className="text-button" onClick={onStart}>Get started <ArrowRight size={15}/></button></div>}
    <div className="tip-banner"><div className="tip-icon"><Sparkles size={17}/></div><p><strong>Consistency beats intensity.</strong> Every session is a step forward. Keep showing up.</p></div>
  </>
}

function StatCard({ label, value, icon, foot }) {
  return <div className="stat-card"><div className="stat-top"><span>{label}</span><span className="stat-icon">{icon}</span></div><div className="stat-value">{value}</div><div className="stat-foot">{foot}</div></div>
}

function HistoryScreen({ workouts }) {
  return <><div className="page-heading"><div><span className="section-kicker">YOUR TIMELINE</span><h1>Workout history<span className="heading-period">.</span></h1><p>A record of the work you've put in.</p></div></div>
    {workouts.length ? <div className="history-list">{workouts.map((workout) => <div className="history-row" key={workout.id}><div className="recent-date-icon"><Dumbbell size={19}/></div><div className="recent-main"><strong>Strength training</strong><span>{formatDate(workout.startedAt)} · {workout.sets.length} sets · {workout.totalReps} reps</span></div><div className="recent-metric"><span className="metric-label">DURATION</span><strong>{formatDuration(workout.durationSeconds)}</strong></div><div className="recent-metric volume-metric"><span className="metric-label">VOLUME</span><strong>{Math.round(workout.totalVolumeKg).toLocaleString()} <small>kg</small></strong></div></div>)}</div>
      : <div className="empty-card"><div className="empty-icon"><Clock3 size={20}/></div><div><strong>No completed workouts yet.</strong><p>Your finished sessions will be saved here for you to look back on.</p></div></div>}
  </>
}

function WorkoutScreen({ workout, exercises, onAddSet, onCreateExercise, onFinish, onBack, busy }) {
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState(null)
  const [reps, setReps] = useState('10')
  const [weight, setWeight] = useState('20')
  const [seconds, setSeconds] = useState(workout.durationSeconds)
  const [showCreate, setShowCreate] = useState(false)
  const [adding, setAdding] = useState(false)
  useEffect(() => { setSeconds(workout.durationSeconds) }, [workout.durationSeconds])
  useEffect(() => {
    const start = Date.now() - seconds * 1000
    const interval = setInterval(() => setSeconds(Math.floor((Date.now() - start) / 1000)), 1000)
    return () => clearInterval(interval)
  }, [])
  const matching = useMemo(() => exercises.filter((exercise) => `${exercise.name} ${exercise.muscleGroup} ${exercise.equipment}`.toLowerCase().includes(search.toLowerCase())), [exercises, search])
  const submitSet = async (event) => {
    event.preventDefault()
    if (!selected) return
    const saved = await onAddSet(selected, reps, weight)
    if (saved) { setSelected(null); setAdding(false) }
  }
  const create = async (form) => { const result = await onCreateExercise(form); if (result) { setSelected(result); setSearch(result.name); setShowCreate(false) } }
  return <>
    <div className="workout-top"><button className="back-button" onClick={onBack} aria-label="Back"><ArrowLeft size={17}/></button><div><span className="section-kicker">WORKOUT IN PROGRESS</span><h1>Strength training<span className="heading-period">.</span></h1></div><span className="live-pill"><span/> LIVE</span></div>
    <div className="workout-summary"><div className="timer-box"><span className="metric-label"><Clock3 size={13}/> SESSION TIME</span><strong>{formatDuration(seconds)}</strong><span>Timer started with your workout</span></div><div className="summary-stat"><span className="metric-label">TOTAL VOLUME</span><strong>{Math.round(workout.totalVolumeKg).toLocaleString()} <small>kg</small></strong><span>Reps × weight</span></div><div className="summary-stat"><span className="metric-label">SETS LOGGED</span><strong>{workout.sets.length}</strong><span>{workout.totalReps} total reps</span></div><button className="finish-button" onClick={onFinish} disabled={busy}><Check size={17}/>{busy ? 'Saving…' : 'Finish workout'}</button></div>
    <div className="workout-columns"><section className="workout-log"><div className="section-heading compact-heading"><div><span className="section-kicker">SESSION LOG</span><h2>Sets logged <span className="count-badge">{workout.sets.length}</span></h2></div></div>
      {workout.sets.length ? <div className="set-list">{workout.sets.map((set, index) => <div className="set-row" key={set.id}><span className="set-number">{String(index + 1).padStart(2, '0')}</span><div className="set-exercise"><strong>{set.exerciseName}</strong><span>{set.muscleGroup}</span></div><div className="set-detail"><strong>{set.reps} <small>reps</small></strong><strong>{Number(set.weightKg).toLocaleString()} <small>kg</small></strong></div><div className="set-volume"><span className="metric-label">VOLUME</span><strong>{Math.round(set.volumeKg).toLocaleString()} kg</strong></div></div>)}</div>
        : <div className="log-empty"><div className="empty-icon"><Dumbbell size={19}/></div><strong>Nothing logged yet</strong><p>Search an exercise to log your first set.</p></div>}
      <div className="exercise-picker"><div className="section-heading compact-heading"><div><span className="section-kicker">BUILD YOUR SESSION</span><h2>Add an exercise</h2></div></div><div className="search-wrap"><Search size={17}/><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search 30+ exercises…" aria-label="Search exercises"/></div>
        <div className="exercise-results">{matching.slice(0, 7).map((exercise) => <button className={`exercise-option ${selected?.id === exercise.id ? 'active-exercise' : ''}`} key={exercise.id} onClick={() => { setSelected(exercise); setAdding(true) }}><span className="exercise-small-icon"><Dumbbell size={15}/></span><span className="exercise-option-main"><strong>{exercise.name}</strong><span>{exercise.muscleGroup} · {exercise.equipment}</span></span>{exercise.custom && <span className="custom-tag">YOURS</span>}<Plus size={16}/></button>)}
          {!matching.length && <div className="no-results">No exercises found. Add it as a custom exercise below.</div>}
        </div>
        <button className="add-custom-button" onClick={() => setShowCreate(true)}><Plus size={15}/> Add a custom exercise</button>
      </div>
    </section>
    <aside className="set-entry-card"><span className="section-kicker">LOG A SET</span><h2>{adding && selected ? selected.name : 'Choose an exercise'}</h2><p>{adding && selected ? `${selected.muscleGroup} · ${selected.equipment}` : 'Select an exercise from the list to get started.'}</p>
      {adding && selected && <form onSubmit={submitSet} className="set-form"><label>Repetitions<input type="number" min="1" max="1000" value={reps} onChange={(e) => setReps(e.target.value)} required/></label><label>Weight <span className="input-unit">KG</span><input type="number" min="0" step="0.5" value={weight} onChange={(e) => setWeight(e.target.value)} required/></label><div className="volume-preview"><span>SET VOLUME</span><strong>{Math.round((Number(reps) || 0) * (Number(weight) || 0)).toLocaleString()} <small>kg</small></strong><em>reps × weight</em></div><button className="primary-button log-button" disabled={busy}>Add set <Plus size={16}/></button></form>}
      {!adding && <div className="entry-placeholder"><div className="entry-placeholder-icon"><ArrowLeft size={17}/></div>Choose an exercise to enter your set.</div>}
      <div className="entry-tip"><Sparkles size={15}/><span>Progress is built one set at a time.</span></div>
    </aside></div>
    {showCreate && <CreateExerciseModal onClose={() => setShowCreate(false)} onSave={create} busy={busy}/>}
  </>
}

function CreateExerciseModal({ onClose, onSave, busy }) {
  const [values, setValues] = useState({ name: '', muscleGroup: 'Chest', equipment: 'Barbell' })
  const change = (key) => (event) => setValues((old) => ({ ...old, [key]: event.target.value }))
  return <div className="modal-backdrop" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}><div className="modal-card" role="dialog" aria-modal="true" aria-labelledby="custom-title"><div className="modal-heading"><div><span className="section-kicker">MAKE IT YOURS</span><h2 id="custom-title">Add an exercise</h2></div><button className="icon-button" onClick={onClose} aria-label="Close"><X size={18}/></button></div><p className="modal-copy">This exercise will be available in your workouts.</p><form className="set-form" onSubmit={(e) => { e.preventDefault(); onSave(values) }}><label>Exercise name<input autoFocus value={values.name} onChange={change('name')} placeholder="e.g. Landmine press" maxLength="100" required/></label><label>Muscle group<select value={values.muscleGroup} onChange={change('muscleGroup')}>{['Chest', 'Back', 'Shoulders', 'Legs', 'Arms', 'Core', 'Full body', 'Other'].map((item) => <option key={item}>{item}</option>)}</select></label><label>Equipment<select value={values.equipment} onChange={change('equipment')}>{['Barbell', 'Dumbbell', 'Cable', 'Machine', 'Bodyweight', 'Kettlebell', 'Other'].map((item) => <option key={item}>{item}</option>)}</select></label><button className="primary-button log-button" disabled={busy}>Save exercise <Plus size={16}/></button></form></div></div>
}

export default App
