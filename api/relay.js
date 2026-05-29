// ── OTP Detection ─────────────────────────────────────────────────────────────
//
// Two-step filter:
//   1. Message must contain at least one OTP-related keyword
//   2. Message must contain a recognisable code (prefixed alphanumeric, mixed, or numeric)
//
// Nothing is stored — fire and forget.

const OTP_KEYWORD_REGEX =
  /\b(otp|one[\s\-]?time|passcode|verification[\s\-]?code|verif[iy]|auth(?:entication)?[\s\-]?code|2fa|two[\s\-]?factor|login[\s\-]?code|security[\s\-]?code|expir[ey][sd]?|do not share|access[\s\-]?code|temporary[\s\-]?code|your code|use.*code|enter.*code)\b/i;

function extractOtp(text) {
  if (!OTP_KEYWORD_REGEX.test(text)) return null;

  // 1. Prefixed alphanumeric  e.g. AB-123456  or  HDFC-847291
  let m = text.match(/\b([A-Z]{1,4}-\d{4,8})\b/);
  if (m) return m[1];

  // 2. Mixed alphanumeric  e.g. AB1234  (uppercase, 4-8 chars, ≥1 letter + ≥1 digit)
  const tokens = text.match(/\b[A-Z0-9]{4,8}\b/g) || [];
  const mixed = tokens.find((t) => /[A-Z]/.test(t) && /\d/.test(t));
  if (mixed) return mixed;

  // 3. Pure numeric  e.g. 847291  (4-8 digits)
  m = text.match(/\b(\d{4,8})\b/);
  if (m) return m[1];

  return null;
}

// ── Telegram ──────────────────────────────────────────────────────────────────

async function sendTelegram(chatId, text) {
  const res = await fetch(
    `https://api.telegram.org/bot${process.env.TELEGRAM_BOT_TOKEN}/sendMessage`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: chatId, text, parse_mode: "HTML" }),
    }
  );
  if (!res.ok) {
    const err = await res.text();
    throw new Error(`Telegram error: ${err}`);
  }
  return res.json();
}

// ── Handler ───────────────────────────────────────────────────────────────────

module.exports = async function handler(req, res) {
  res.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");

  if (req.method === "OPTIONS") return res.status(200).end();
  if (req.method !== "POST")
    return res.status(405).json({ error: "Method not allowed" });

  const { secret, message, sender, from } = req.body || {};

  // ── Auth ──
  if (!secret || secret !== process.env.SECRET_KEY) {
    return res.status(401).json({ error: "Unauthorized" });
  }

  if (!message || !from) {
    return res
      .status(400)
      .json({ error: "Missing required fields: message, from" });
  }

  // ── OTP filter ──
  const otp = extractOtp(message);
  if (!otp) {
    return res
      .status(200)
      .json({ skipped: true, reason: "Not an OTP message" });
  }

  // ── Routing: sender phone → recipient Telegram chat ID ──
  const routes = {
    [process.env.YOUR_PHONE]: process.env.WIFE_CHAT_ID,
    [process.env.WIFE_PHONE]: process.env.YOUR_CHAT_ID,
  };

  const targetChatId = routes[from];
  if (!targetChatId) {
    return res.status(400).json({ error: "Unknown from phone number" });
  }

  const tgText = `🔐 <b>OTP from ${sender || "Unknown"}</b>\n\n<code>${otp}</code>`;

  try {
    await sendTelegram(targetChatId, tgText);
    return res.status(200).json({ success: true, otp });
  } catch (err) {
    return res.status(500).json({ error: err.message });
  }
};
