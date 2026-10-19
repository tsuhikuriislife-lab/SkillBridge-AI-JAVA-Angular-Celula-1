import { mkdirSync, writeFileSync } from 'node:fs';

mkdirSync('public', { recursive: true });
const apiUrl = process.env.API_URL || '/api';
writeFileSync('public/env.js', `window.__env = { API_URL: ${JSON.stringify(apiUrl)} };\n`);
console.log(`Generated public/env.js with API_URL=${apiUrl}`);
