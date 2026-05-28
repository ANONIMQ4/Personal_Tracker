(function () {
    const unsafeMethods = new Set(["POST", "PUT", "PATCH", "DELETE"]);

    function getCookie(name) {
        return document.cookie
            .split(";")
            .map((item) => item.trim())
            .find((item) => item.startsWith(`${name}=`))
            ?.slice(name.length + 1) || "";
    }

    function csrfToken() {
        const token = getCookie("XSRF-TOKEN");
        return token ? decodeURIComponent(token) : "";
    }

    function isFormData(value) {
        return typeof FormData !== "undefined" && value instanceof FormData;
    }

    function shouldSerializeJson(value) {
        return value != null
            && typeof value === "object"
            && !isFormData(value)
            && !(value instanceof Blob)
            && !(value instanceof ArrayBuffer)
            && !(value instanceof URLSearchParams);
    }

    async function parseError(response) {
        const contentType = response.headers.get("content-type") || "";
        if (contentType.includes("application/json")) {
            const error = await response.json().catch(() => null);
            if (error?.message) {
                return error.message;
            }
        }
        const text = await response.text().catch(() => "");
        return text || "Ошибка запроса";
    }

    async function apiFetch(path, options = {}) {
        const redirectOnUnauthorized = options.redirectOnUnauthorized !== false;
        const fetchOptions = { ...options };
        delete fetchOptions.redirectOnUnauthorized;

        const method = (options.method || "GET").toUpperCase();
        const headers = new Headers(options.headers || {});
        let body = options.body;

        if (shouldSerializeJson(body)) {
            body = JSON.stringify(body);
        }
        if (body != null && !isFormData(body) && !headers.has("Content-Type")) {
            headers.set("Content-Type", "application/json");
        }
        if (unsafeMethods.has(method)) {
            const token = csrfToken();
            if (token) {
                headers.set("X-XSRF-TOKEN", token);
            }
        }

        const response = await fetch(path, {
            ...fetchOptions,
            method,
            headers,
            body,
            credentials: "same-origin"
        });

        if (response.status === 401 && redirectOnUnauthorized) {
            window.location.href = "/login";
            return null;
        }
        if (!response.ok) {
            throw new Error(await parseError(response));
        }
        if (response.status === 204) {
            return null;
        }

        const contentType = response.headers.get("content-type") || "";
        if (contentType.includes("application/json")) {
            return response.json();
        }
        return response.text();
    }

    window.getCookie = getCookie;
    window.csrfToken = csrfToken;
    window.apiFetch = apiFetch;
})();
