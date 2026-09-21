/**
 * REST API Client configured for Spring Boot Backend (JavaScript)
 * Supports Session Cookies, unified error handling, and JSON serialization.
 */

// Base URL for Spring Boot backend - mapped via Vite proxy in dev
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export class ApiError extends Error {
  constructor(message, status, data) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.data = data;
  }
}

class ApiClient {
  constructor(baseURL) {
    this.baseURL = baseURL;
  }

  getBaseUrl() {
    return this.baseURL;
  }

  getHeaders(customHeaders = {}) {
    // Xóa Content-Type khỏi customHeaders nếu nó là null để tránh khởi tạo thành chuỗi "null"
    const cleanedHeaders = { ...customHeaders };
    let removeContentType = false;
    
    if (cleanedHeaders['Content-Type'] === null) {
      delete cleanedHeaders['Content-Type'];
      removeContentType = true;
    }

    const headers = new Headers(cleanedHeaders);
    
    if (!headers.has('Content-Type') && !removeContentType) {
      headers.set('Content-Type', 'application/json');
    }
    
    headers.set('Accept', 'application/json');
    return headers;
  }

  async request(endpoint, options = {}) {
    const url = `${this.baseURL}${endpoint.startsWith('/') ? endpoint : `/${endpoint}`}`;
    const headers = this.getHeaders(options.headers);

    try {
      const response = await fetch(url, {
        ...options,
        headers,
        credentials: 'include', // Important for Session Cookies
      });

      if (!response.ok) {
        const text = await response.text();
        let errorData = null;
        try {
          errorData = text ? JSON.parse(text) : null;
        } catch {
          errorData = text;
        }

        const message =
          (errorData && typeof errorData === 'object' && (errorData.message || errorData.error)) ||
          `Request failed with status ${response.status}: ${response.statusText}`;

        throw new ApiError(message, response.status, errorData);
      }

      // Handle 204 No Content or endpoints returning empty string
      if (response.status === 204) {
        return null;
      }
      
      const text = await response.text();
      return text ? JSON.parse(text) : null;
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }
      throw new ApiError(
        error?.message || 'Không thể kết nối tới máy chủ Spring Boot (Network Error)',
        0,
        error
      );
    }
  }

  get(endpoint, headers) {
    return this.request(endpoint, { method: 'GET', headers });
  }

  post(endpoint, body, headers = {}) {
    // Check if body is FormData or URLSearchParams, don't stringify
    const isFormData = body instanceof FormData || body instanceof URLSearchParams;
    
    // If it's URLSearchParams, we need to make sure Content-Type is application/x-www-form-urlencoded
    // Fetch does this automatically for URLSearchParams
    if (isFormData) {
        headers['Content-Type'] = null; // Let fetch handle it
    }

    return this.request(endpoint, {
      method: 'POST',
      body: isFormData ? body : JSON.stringify(body),
      headers,
    });
  }

  put(endpoint, body, headers) {
    return this.request(endpoint, {
      method: 'PUT',
      body: JSON.stringify(body),
      headers,
    });
  }

  patch(endpoint, body, headers) {
    return this.request(endpoint, {
      method: 'PATCH',
      body: JSON.stringify(body),
      headers,
    });
  }

  delete(endpoint, headers) {
    return this.request(endpoint, { method: 'DELETE', headers });
  }
}

export const apiClient = new ApiClient(API_BASE_URL);
