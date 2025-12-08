import { useState } from 'react';
import axios from 'axios';
import { useNavigate, Link } from 'react-router-dom';

export default function Login() {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const handleLogin = async (e) => {
        e.preventDefault();
        setError('');
        try {
            const res = await axios.post('/api/users/login', { email, password });
            localStorage.setItem('user', JSON.stringify(res.data));
            navigate('/dashboard');
        } catch (err) {
            console.error(err);
            setError(err.response?.data || 'Login failed');
        }
    };

    return (
        <div className="auth-page">
            <div className="card">
                <h2 style={{ marginTop: 0, marginBottom: '1.5rem' }}>Welcome Back</h2>
                {error && <div style={{ color: '#ef4444', marginBottom: '1rem' }}>{error}</div>}
                <form onSubmit={handleLogin} className="flex-col">
                    <input className="input" placeholder="Email" value={email} onChange={e => setEmail(e.target.value)} />
                    <input className="input" type="password" placeholder="Password" value={password} onChange={e => setPassword(e.target.value)} />
                    <button className="btn" type="submit">Log In</button>
                </form>
                <p style={{ marginTop: '1rem', color: 'var(--text-muted)', textAlign: 'center' }}>
                    Don't have an account? <Link to="/register" style={{ color: 'var(--primary)', textDecoration: 'none' }}>Register</Link>
                </p>
            </div>
        </div>
    );
}
