const form = document.querySelector("#registration-form");
    const message = document.querySelector("#message");
    const button = form.querySelector("button");
    const defaultButtonText = button.textContent;

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        message.textContent = "";
        message.className = "message";
        button.disabled = true;
        button.textContent = "Создаю...";

        const formData = new FormData(form);
        const user = {
            username: formData.get("username"),
            email: formData.get("email"),
            password: formData.get("password")
        };

        try {
            await apiFetch("/users", {
                method: "POST",
                body: user,
                redirectOnUnauthorized: false
            });

            form.reset();
            message.textContent = "Аккаунт создан. Сейчас откроем вход...";
            message.classList.add("success");
            setTimeout(() => {
                window.location.href = "/login";
            }, 950);
        } catch (error) {
            message.textContent = error.message;
            message.classList.add("error");
            button.disabled = false;
            button.textContent = defaultButtonText;
        }
    });
