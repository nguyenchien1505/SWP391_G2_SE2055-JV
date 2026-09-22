import axios from "axios";

export const api = axios.create({
  baseURL: "/api",
  timeout: 30000,
  withCredentials: true,
});

export function errorMessage(err: unknown, fallback = "Đã có lỗi xảy ra"): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as { message?: string; error?: string } | undefined;
    return data?.message ?? data?.error ?? fallback;
  }
  return fallback;
}
