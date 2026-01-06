import { useState } from 'react';
import axios from 'axios';
import { useNavigate, Link } from 'react-router-dom';

export default function Register() {
    const [name, setName] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const navigate = useNavigate();

    const handleRegister = async (e) => {
        e.preventDefault();
        setError('');
        try {
            const authHeader = 'Basic ' + btoa(email + ':' + password);
            const res = await axios.post('/api/users/register', { name }, {
                headers: { 'Authorization': authHeader }
            });
            localStorage.setItem('user', JSON.stringify(res.data));
            navigate('/dashboard');
        } catch (err) {
            console.error(err);
            setError(err.response?.data || 'Registration failed');
        }
    };

    return (
        <div className="auth-page">
            <div className="card">
                <h2 style={{ marginTop: 0, marginBottom: '1.5rem' }}>Create Account</h2>
                {error && <div style={{ color: '#ef4444', marginBottom: '1rem' }}>{error}</div>}
                <form onSubmit={handleRegister} className="flex-col">
                    <input className="input" placeholder="Full Name" value={name} onChange={e => setName(e.target.value)} />
                    <input className="input" placeholder="Email" value={email} onChange={e => setEmail(e.target.value)} />
                    <input className="input" type="password" placeholder="Password" value={password} onChange={e => setPassword(e.target.value)} />
                    <button className="btn" type="submit">Sign Up</button>
                </form>
                <p style={{ marginTop: '1rem', color: 'var(--text-muted)', textAlign: 'center' }}>
                    Already have an account? <Link to="/login" style={{ color: 'var(--primary)', textDecoration: 'none' }}>Log In</Link>
                </p>
            </div>
        </div>
    );
}
