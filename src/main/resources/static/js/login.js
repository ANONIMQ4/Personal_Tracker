const form = document.querySelector("#login-form");
    const message = document.querySelector("#message");
    const button = form.querySelector("button");
    const defaultButtonText = button.textContent;

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        message.textContent = "";
        message.className = "message";
        button.disabled = true;
        button.textContent = "Вхожу...";

        const formData = new FormData(form);
        const credentials = {
            login: formData.get("login"),
            password: formData.get("password")
        };

        try {
            await apiFetch("/login", {
                method: "POST",
                body: credentials,
                redirectOnUnauthorized: false
            });

            message.textContent = "Готово. Открываем dashboard...";
            message.classList.add("success");
            window.location.href = "/myacc";
        } catch (error) {
            message.textContent = "Неверный логин или пароль";
            message.classList.add("error");
            button.disabled = false;
            button.textContent = defaultButtonText;
        }
    });
