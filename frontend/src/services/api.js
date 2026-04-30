import axios from 'axios';

const API_URL = import.meta.env.VITE_API_BASE_URL || '/api';
const api = axios.create({ baseURL: API_URL });

// ── Request interceptor: attach JWT ──────────────────────────────────────────
api.interceptors.request.use(config => {
  const token = localStorage.getItem('sc_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// ── Response interceptor: handle auth errors globally ────────────────────────
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      // Token expired or invalid — clear storage and redirect to login
      localStorage.removeItem('sc_token');
      localStorage.removeItem('sc_user');
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

// ── Auth ──────────────────────────────────────────────────────────────────────
export const authApi = {
  signup: (data) => api.post('/auth/signup', data),
  login:  (data) => api.post('/auth/login',  data),
};

// ── Groups ────────────────────────────────────────────────────────────────────
export const groupApi = {
  create:       (data)         => api.post('/groups/create',                   data),
  join:         (data)         => api.post('/groups/join',                     data),
  get:          (id)           => api.get(`/groups/${id}`),
  listMyGroups: ()             => api.get('/groups/my'),
  setThreshold: (id, data)     => api.put(`/groups/${id}/threshold`,           data),
  removeMember: (groupId, uid) => api.delete(`/groups/${groupId}/members/${uid}`),
  updateRoute:  (id, data)     => api.put(`/groups/${id}/route`,               data),
};

// ── Locations ─────────────────────────────────────────────────────────────────
export const locationApi = {
  update:   (data)    => api.post('/locations/update',        data),
  getGroup: (groupId) => api.get(`/locations/group/${groupId}`),
};

export default api;
