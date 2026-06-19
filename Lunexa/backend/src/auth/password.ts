const encoder = new TextEncoder();
const iterations = 100_000;

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }

  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function fromBase64Url(value: string): Uint8Array {
  const padded = value.replaceAll("-", "+").replaceAll("_", "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);

  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }

  return bytes;
}

async function derive(password: string, salt: Uint8Array, rounds: number): Promise<Uint8Array> {
  const saltBuffer = salt.buffer.slice(salt.byteOffset, salt.byteOffset + salt.byteLength) as ArrayBuffer;
  const key = await crypto.subtle.importKey("raw", encoder.encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    {
      name: "PBKDF2",
      hash: "SHA-256",
      salt: saltBuffer,
      iterations: rounds,
    },
    key,
    256,
  );

  return new Uint8Array(bits);
}

function timingSafeEqual(left: Uint8Array, right: Uint8Array): boolean {
  if (left.length !== right.length) return false;

  let diff = 0;
  for (let index = 0; index < left.length; index += 1) {
    diff |= left[index] ^ right[index];
  }

  return diff === 0;
}

export async function hashPassword(password: string): Promise<string> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const hash = await derive(password, salt, iterations);
  return `pbkdf2-sha256$${iterations}$${base64Url(salt)}$${base64Url(hash)}`;
}

export async function verifyPassword(password: string, storedHash: string): Promise<boolean> {
  const [algorithm, roundsValue, saltValue, hashValue] = storedHash.split("$");
  if (algorithm !== "pbkdf2-sha256" || !roundsValue || !saltValue || !hashValue) {
    return false;
  }

  const rounds = Number(roundsValue);
  if (!Number.isInteger(rounds) || rounds < 100_000) {
    return false;
  }

  const salt = fromBase64Url(saltValue);
  const expected = fromBase64Url(hashValue);
  const actual = await derive(password, salt, rounds);

  return timingSafeEqual(actual, expected);
}
