const encoder = new TextEncoder();

function toBase64(bytes: Uint8Array): string {
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary);
}

function fromBase64(value: string): Uint8Array {
  const binary = atob(value);
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index += 1) {
    bytes[index] = binary.charCodeAt(index);
  }
  return bytes;
}

function joinBytes(left: Uint8Array, right: Uint8Array): Uint8Array {
  const output = new Uint8Array(left.length + right.length);
  output.set(left);
  output.set(right, left.length);
  return output;
}

function timingSafeEqual(left: Uint8Array, right: Uint8Array): boolean {
  if (left.length !== right.length) return false;

  let difference = 0;
  for (let index = 0; index < left.length; index += 1) {
    difference |= left[index] ^ right[index];
  }
  return difference === 0;
}

async function sha256(bytes: Uint8Array): Promise<Uint8Array> {
  const buffer = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(buffer).set(bytes);
  const hash = await crypto.subtle.digest("SHA-256", buffer);
  return new Uint8Array(hash);
}

export function generateVerificationCode(): string {
  const random = new Uint32Array(1);
  crypto.getRandomValues(random);
  return String(random[0] % 1_000_000).padStart(6, "0");
}

export async function hashVerificationCode(code: string): Promise<string> {
  const salt = new Uint8Array(16);
  crypto.getRandomValues(salt);
  const hash = await sha256(joinBytes(salt, encoder.encode(code)));

  return `v1$${toBase64(salt)}$${toBase64(hash)}`;
}

export async function verifyVerificationCode(code: string, storedHash: string): Promise<boolean> {
  const [version, saltValue, hashValue] = storedHash.split("$");
  if (version !== "v1" || !saltValue || !hashValue) {
    return false;
  }

  const salt = fromBase64(saltValue);
  const expected = fromBase64(hashValue);
  const actual = await sha256(joinBytes(salt, encoder.encode(code)));

  return timingSafeEqual(actual, expected);
}
