# MedAtlas AI • Cloudflare Worker

**ChatGPT Plus** can be used via the Android share sheet, without any backend. Embedded AI uses **OpenAI API billed separately**; never copy the OpenAI secret into the app or GitHub.

## Deployment

1. Create an OpenAI API account with billing enabled and obtain an API key (https://platform.openai.com/api-keys).
2. From `backend/`, run `npx wrangler login` and then `npx wrangler deploy`.
3. Set secrets with `npx wrangler secret put OPENAI_API_KEY` and `npx wrangler secret put MEDATLAS_APP_TOKEN`. Use a random secret for the second one. Do **not** put them into git, screenshots or chat.
4. Redeploy if needed, find your `https://medatlas-nadia-ai.<subdomain>.workers.dev` URL and enter it along with the app token in **MedAtlas → Setări → Nadia AI**.
5. Test `GET /health` (does not reveal a secret). The `POST /ai` endpoint requires the app token.

Modes: `explain`, `summary`, `quiz`, `free`, `highlights`, `web` (online sources), and `sticker` (medical illustration PNG). The AI client only sends document excerpts or selected images on explicit user actions. `store:false` is set on Responses API calls. Medical illustrations are **AI-generated and not anatomically validated**.

Important: this is a personal-use backend protected by a shared secret. For multiple users, implement user authentication, per-user quotas, rate limits, monitoring and key rotation before deployment. Do not use patient-identifiable information.
