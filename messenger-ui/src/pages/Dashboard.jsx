import { useState, useEffect, useRef } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import { Search, Send, LogOut, User, MessageCircle } from 'lucide-react';

export default function Dashboard() {
    const [currentUser, setCurrentUser] = useState(() => JSON.parse(localStorage.getItem('user')));
    const [chats, setChats] = useState([]);
    const [selectedChat, setSelectedChat] = useState(null);
    const [messages, setMessages] = useState([]);
    const [messageInput, setMessageInput] = useState('');
    const [searchResults, setSearchResults] = useState([]);
    const [ws, setWs] = useState(null);
    const navigate = useNavigate();
    const messagesEndRef = useRef(null);
    const wsRef = useRef(null);
    const selectedChatRef = useRef(null);

    useEffect(() => {
        selectedChatRef.current = selectedChat;
        if (selectedChat) {
            loadMessages(selectedChat.id);
        }
    }, [selectedChat]);

    useEffect(() => {
        if (!currentUser) {
            navigate('/login');
            return;
        }

        // Set Auth Header
        axios.defaults.headers.common['Authorization'] = `Bearer ${currentUser.token}`;

        // Load Chats
        loadChats();

        // Connect WebSocket
        connectWebSocket();

        return () => {
            if (wsRef.current) wsRef.current.close();
        };
    }, [currentUser]);

    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    const connectWebSocket = async () => {
        try {
            // 1. Get Service Address from Registry
            const res = await axios.get(`/api/registry/user/${currentUser.id}`);
            const assignment = res.data;
            // assignment = { serviceId, address }
            // address like "ws://localhost:8085/ws"

            if (wsRef.current) wsRef.current.close();

            const wsUrl = `${assignment.address}?userId=${currentUser.id}&token=${currentUser.token}`;
            console.log('Connecting to', wsUrl);
            const socket = new WebSocket(wsUrl);

            socket.onopen = () => console.log('WebSocket Connected');
            socket.onmessage = (event) => {
                try {
                    const msg = JSON.parse(event.data);
                    // Check if msg belongs to current chat
                    if (selectedChatRef.current && msg.chatId === selectedChatRef.current.id) {
                        setMessages(prev => [...prev, msg]);
                    }
                } catch (e) {
                    console.error('Error parsing WS message', e);
                }
            };

            wsRef.current = socket;
            setWs(socket);

        } catch (err) {
            console.error('Failed to connect WebSocket', err);
        }
    };

    const loadChats = async () => {
        try {
            // 1. Get Chat IDs
            const res = await axios.get(`/api/history/user/${currentUser.id}/chats`);
            const chatIds = res.data;

            // 2. Fetch details for each chat to find name
            // This is heavy, but necessary without better API
            const enrichedChats = await Promise.all(chatIds.map(async (chatId) => {
                try {
                    const partsRes = await axios.get(`/api/history/chat/${chatId}/participants`);
                    const userIds = partsRes.data.userIds;
                    const otherUserId = userIds.find(uid => uid !== currentUser.id) || currentUser.id; // Fallback to self

                    // Get User Name
                    const userRes = await axios.get(`/api/users/${otherUserId}`);
                    return {
                        id: chatId,
                        name: userRes.data.name,
                        otherUserId: otherUserId
                    };
                } catch (e) {
                    return { id: chatId, name: 'Unknown' };
                }
            }));

            setChats(enrichedChats);
        } catch (err) {
            console.error('Failed to load chats', err);
        }
    };

    const loadMessages = async (chatId) => {
        try {
            const res = await axios.get(`/api/history/messages`, {
                params: {
                    chatId,
                    from: new Date(Date.now() - 86400000 * 7).toISOString(), // Last 7 days
                    to: new Date().toISOString()
                }
            });
            // Sort messages? Backend usually returns sorted? check.
            // Cassandra order?
            setMessages(res.data);
        } catch (err) {
            console.error('Failed to load messages', err);
        }
    };

    const sendMessage = async (e) => {
        e.preventDefault();
        if (!messageInput.trim() || !selectedChat || !ws) return;

        const msg = {
            chatId: selectedChat.id,
            messageContent: messageInput,
            messageSent: currentUser.name, // "messageSent" field in MessageRequest is sender name?
            userId: currentUser.id
        };

        // Send via WebSocket
        ws.send(JSON.stringify(msg));

        // Optimistic Update?
        // Wait for echo?
        // Backend broadcasts back to Sender via Kafka -> Redis -> WS.
        // So we should receive it in onmessage.

        setMessageInput('');
    };

    const handleSearch = async (query) => {
        if (!query) {
            setSearchResults([]);
            return;
        }
        try {
            const res = await axios.get(`/api/users/search`, { params: { query } });
            setSearchResults(res.data);
        } catch (err) {
            console.error(err);
        }
    };

    const startChat = async (targetUser) => {
        try {
            const res = await axios.get(`/api/history/chat`, {
                params: { userId1: currentUser.id, userId2: targetUser.id }
            });
            const chatId = res.data;

            // Check if exists in list
            let existing = chats.find(c => c.id === chatId);
            if (!existing) {
                existing = { id: chatId, name: targetUser.name, otherUserId: targetUser.id };
                setChats(prev => [existing, ...prev]);
            }
            setSelectedChat(existing);
            setSearchResults([]);
        } catch (err) {
            alert('Failed to start chat');
        }
    };

    const logout = () => {
        localStorage.removeItem('user');
        navigate('/login');
    };

    return (
        <div className="flex-col" style={{ height: '100vh', gap: 0 }}>
            {/* Navbar */}
            <div style={{
                height: '60px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center',
                padding: '0 2rem', justifyContent: 'space-between', background: 'var(--bg-card)'
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', position: 'relative' }}>
                    <h2 style={{ margin: 0, color: 'var(--primary)' }}>Neyma Messenger</h2>
                    <div style={{ position: 'relative' }}>
                        <div className="flex-center" style={{ background: 'var(--input-bg)', borderRadius: '20px', padding: '0.5rem 1rem' }}>
                            <Search size={18} style={{ marginRight: '0.5rem', color: 'var(--text-muted)' }} />
                            <input
                                style={{ background: 'transparent', border: 'none', color: 'white', outline: 'none', width: '200px' }}
                                placeholder="Search users..."
                                onChange={e => handleSearch(e.target.value)}
                            />
                        </div>
                        {searchResults.length > 0 && (
                            <div className="card" style={{ position: 'absolute', top: '100%', left: 0, zIndex: 10, padding: '10px', width: '100%', marginTop: '5px' }}>
                                {searchResults.map(u => (
                                    <div key={u.id}
                                        style={{ padding: '10px', cursor: 'pointer', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: '10px' }}
                                        onClick={() => startChat(u)}>
                                        <User size={20} />
                                        <span>{u.name}</span>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
                <div className="flex-center" style={{ gap: '1rem' }}>
                    <span>{currentUser?.name}</span>
                    <button onClick={logout} style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}>
                        <LogOut size={20} />
                    </button>
                </div>
            </div>

            {/* Main Content */}
            <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
                {/* Sidebar */}
                <div style={{ width: '300px', borderRight: '1px solid var(--border)', background: 'var(--bg-dark)', overflowY: 'auto' }}>
                    {chats.map(chat => (
                        <div key={chat.id}
                            onClick={() => setSelectedChat(chat)}
                            style={{
                                padding: '1rem', borderBottom: '1px solid var(--border)', cursor: 'pointer',
                                background: selectedChat?.id === chat.id ? 'var(--bg-card)' : 'transparent',
                                display: 'flex', alignItems: 'center', gap: '1rem'
                            }}>
                            <div style={{ width: '40px', height: '40px', borderRadius: '50%', background: 'var(--primary)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                                {chat.name.charAt(0).toUpperCase()}
                            </div>
                            <div>
                                <div style={{ fontWeight: 'bold' }}>{chat.name}</div>
                                <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>Click to chat</div>
                            </div>
                        </div>
                    ))}
                </div>

                {/* Chat Area */}
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', background: 'var(--bg-dark)' }}>
                    {selectedChat ? (
                        <>
                            <div style={{ padding: '1rem', borderBottom: '1px solid var(--border)', background: 'var(--bg-card)' }}>
                                <h3>{selectedChat.name}</h3>
                            </div>

                            <div style={{ flex: 1, overflowY: 'auto', padding: '2rem', display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                                {messages.map((msg, idx) => {
                                    const isMe = msg.sender === currentUser.id || msg.userId === currentUser.id; // Check field name in Message entity/DTO
                                    // Message entity: userId (Sender), messageContent.
                                    // KafkaMessage: sender, receiver, message.
                                    // Received from WS: KafkaMessage structure?
                                    // Let's assume standard fields: 
                                    // 'sender' UUID.
                                    // Check logic below for isMe.
                                    // Actually, let's debug standard message fields later.
                                    return (
                                        <div key={idx} style={{
                                            alignSelf: (msg.sender === currentUser.id || msg.userId === currentUser.id) ? 'flex-end' : 'flex-start',
                                            maxWidth: '70%'
                                        }}>
                                            <div style={{
                                                background: (msg.sender === currentUser.id || msg.userId === currentUser.id) ? 'var(--chat-sent)' : 'var(--chat-received)',
                                                padding: '0.75rem 1rem', borderRadius: '1rem',
                                                borderBottomRightRadius: (msg.sender === currentUser.id || msg.userId === currentUser.id) ? '2px' : '1rem',
                                                borderBottomLeftRadius: (msg.sender === currentUser.id || msg.userId === currentUser.id) ? '1rem' : '2px',
                                            }}>
                                                {msg.message || msg.messageContent}
                                            </div>
                                            <div style={{ fontSize: '0.7rem', color: 'var(--text-muted)', textAlign: 'right', marginTop: '4px' }}>
                                                {new Date(msg.messageTime || msg.timestamp).toLocaleTimeString()}
                                            </div>
                                        </div>
                                    );
                                })}
                                <div ref={messagesEndRef} />
                            </div>

                            <form onSubmit={sendMessage} style={{ padding: '1rem', background: 'var(--bg-card)', display: 'flex', gap: '1rem' }}>
                                <input
                                    className="input"
                                    value={messageInput}
                                    onChange={e => setMessageInput(e.target.value)}
                                    placeholder="Type a message..."
                                />
                                <button className="btn" type="submit">
                                    <Send size={20} />
                                </button>
                            </form>
                        </>
                    ) : (
                        <div className="flex-center" style={{ flex: 1, color: 'var(--text-muted)', flexDirection: 'column', gap: '1rem' }}>
                            <MessageCircle size={64} style={{ opacity: 0.2 }} />
                            <h2>Select a chat to start messaging</h2>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
