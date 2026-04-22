import axios from 'axios';

const API_URL = import.meta.env.VITE_API_BASE_URL || '/api';
const api = axios.create({ baseURL: API_URL });

// Attach JWT to every request
api.interceptors.request.use(config => {
  const token = localStorage.getItem('sc_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export const authApi = {
  signup: (data) => api.post('/auth/signup', data),
  login:  (data) => api.post('/auth/login', data),
};

export const groupApi = {
  create:       (data)           => api.post('/groups/create', data),
  join:         (data)           => api.post('/groups/join', data),
  get:          (id)             => api.get(`/groups/${id}`),
  listMyGroups: ()               => api.get('/groups/my'),
  setThreshold: (id, data)       => api.put(`/groups/${id}/threshold`, data),
  removeMember: (groupId, uid)   => api.delete(`/groups/${groupId}/members/${uid}`),
  updateRoute:  (id, data)       => api.put(`/groups/${id}/route`, data),
};

export const locationApi = {
  update:      (data)    => api.post('/locations/update', data),
  getGroup:    (groupId) => api.get(`/locations/group/${groupId}`),
};

export default api;
