# web — KharchaKhata frontend

React 18 + Vite + TypeScript + Tailwind. Path alias `@/` → `src/`.

## Dev

```bash
cd web
npm install
npm run dev            # localhost:3000
npm run build          # → dist/
npm run test           # vitest
```

## Runtime config

`API_BASE_URL` and `GOOGLE_CLIENT_ID` are injected at container start via
`docker-entrypoint.sh` into `/env.js`, read by the browser at load time —
no rebuild needed per environment.
