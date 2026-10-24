# EOBApp

A small Android app for [eob_scheduler](https://github.com/robertalks/eob_scheduler): the same
view as the scheduler's web dashboard — current and target temperature, heater status,
electricity tariff, a temperature chart and heater runtime per day — reading the scheduler's
JSON API directly.

It shows what the scheduler and the thermostat are doing, and can change the **low-tariff
target temperature** — nothing else; the scheduler stays in charge of when the heater may run.

---

## Screens

- **Home** — four tiles (Current temperature, Target temperature, Heater status, Electricity
  tariff), a temperature chart (6 h / 24 h / 7 d) with heater-on and low-tariff periods shaded,
  and heater runtime per day for the last 14 days, split into low and high tariff. Heater ON and
  LOW tariff are green, OFF and HIGH are red — same as the web page.
  Refreshes every minute while it's on screen, and straight away when you come back to it.
  Nothing runs in the background.
- **Settings** — the heater address and the Cloudflare Access service token, with a
  **Test connection** button. Shown first on a fresh install.

Light and dark follow the system setting.

## Connecting through Cloudflare Access

The scheduler's dashboard sits behind Cloudflare Access, which normally answers with a login
page — fine in a browser, useless for an app. The app instead sends a **service token** with
every request (`CF-Access-Client-Id` / `CF-Access-Client-Secret` headers), which Access accepts
without a login:

1. Cloudflare **Zero Trust → Access controls → Service credentials → Service Tokens → Create
   Service Token**. Copy the **Client ID** and **Client Secret** — the secret is shown only once.
2. Open the heater's **Access application → Policies → Add a policy**:
   **Action: Service Auth**, **Include → Service Token → the token from step 1**. Keep your
   existing email **Allow** policy; the browser login keeps working alongside.
3. In the app's Settings enter the address (e.g. `heater.example.com`), the Client ID and the
   Client Secret, and tap **Test connection**.

If the token or the Service Auth policy is missing, Access redirects to its login page; the app
reports that in plain words rather than showing a parse error.

The address is always used over HTTPS. Settings are stored with `EncryptedSharedPreferences`
(keys in the Android keystore) and are excluded from Android backups.

## Changing the target temperature

Tap the **Target temperature** tile (it shows a pencil when the scheduler allows it) and pick
a value with − / + in 0.5 °C steps. The scheduler uses it for every low-tariff period: during
low tariff it's applied immediately; during high tariff the heater stays off and the tile shows
"low tariff: …" — the value it will run at next. **Reset to scheduler default** goes back to the
scheduler's own setting.

This needs eob_scheduler with the control API, `control.enabled: true` and a `control.token`
in its `config.yaml`, and the same token entered as **Control token** in the app's Settings.
The token is separate from the Cloudflare service token: Cloudflare decides who may reach the
scheduler at all, the control token who may change the heater.

## API used

All from `eob_stats.py` in eob_scheduler:

| Endpoint | Used for |
|---|---|
| `GET /api/status` | the four tiles |
| `GET /api/history?hours=6\|24\|168` | the temperature chart |
| `GET /api/daily?days=14` | heater runtime per day |
| `POST /api/target` `{"target": 18.5}` | change the low-tariff target (control token) |
| `DELETE /api/target` | back to the scheduler default (control token) |

## Building

Same setup as MovementID: Kotlin, Jetpack Compose, Material 3, OkHttp + Gson, no other chart or
networking libraries.

- **Build** workflow (`.github/workflows/build.yml`) compiles on every push.
- **Release** workflow (`.github/workflows/release.yml`) builds a signed APK when you push a tag:

  ```
  git tag v1.0.0 && git push origin v1.0.0
  ```

  It needs four repository secrets (Settings → Secrets and variables → Actions):
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — see the comment at the
  top of the workflow for how to encode the keystore. The APK is attached to a GitHub Release
  as `EOBApp-<tag>.apk`.

Locally, open the project in Android Studio and run it as usual.

## License

GPL-3.0, see [LICENSE](LICENSE).
