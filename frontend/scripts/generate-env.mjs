import { mkdirSync, writeFileSync } from 'node:fs';

mkdirSync('public', { recursive: true });
const rawApiUrl = process.env.API_URL || '/api';
const apiUrl = rawApiUrl.startsWith('http') ? '/api' : rawApiUrl;
writeFileSync('public/env.js', `window.__env = { API_URL: ${JSON.stringify(apiUrl)} };\n`);
console.log(`Generated public/env.js with API_URL=${apiUrl}`);
