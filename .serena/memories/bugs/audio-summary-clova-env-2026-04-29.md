# Audio summary 400 due to missing Clova env

Symptom: `/counsel/audio/summary/{audioId}` returned 400 and the UI showed `음성 전사 설정이 없어 요약할 녹취 원문을 만들 수 없습니다.` after audio upload.

Root cause: The running Spring Boot process PID 78001 had `OPENAI_API_KEY` but did not have `NCP_CLOVA_INVOKE_URL` or `NCP_CLOVA_SECRET_KEY`. `PageController.isClovaSpeechConfigured()` therefore returned false, so the summary endpoint refused to create transcript text before OpenAI summarization. The STT call path already exists in both upload and summary flows.

Fix applied operationally: Registered Clova/OpenAI vars with `launchctl setenv`, stopped the stale app/Gradle daemon, and restarted `bootRun` in detached screen session `csm-bootrun`. New PID 80730 has all three env vars set and listens on 8081.

Verification: `ps eww -p 80730` showed `NCP_CLOVA_SECRET_KEY=<set>`, `NCP_CLOVA_INVOKE_URL=<set>`, `OPENAI_API_KEY=<set>`. `curl /csm/counsel/inpatient` redirects to login, confirming app responds. `./gradlew classes --console=plain` succeeded.

Residual risk: If the audio file is browser-recorded webm, current backend intentionally skips webm STT and returns unsupported format. Use mp3/wav/m4a upload or add server-side conversion/transcription support later.