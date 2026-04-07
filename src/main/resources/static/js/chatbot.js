(() => {
    const root = document.getElementById("irir-chatbot");
    if (!root) return;

    const launcher = document.getElementById("chatbot-launcher");
    const panel = document.getElementById("chatbot-panel");
    const closeBtn = document.getElementById("chatbot-close");
    const form = document.getElementById("chatbot-form");
    const input = document.getElementById("chatbot-input");
    const messages = document.getElementById("chatbot-messages");

    const addMessage = (text, role, links) => {
        const bubble = document.createElement("div");
        bubble.className = `chatbot-bubble ${role}`;
        bubble.textContent = text;
        if (links && links.length) {
            const linksWrap = document.createElement("div");
            linksWrap.className = "chatbot-links";
            links.forEach(link => {
                const a = document.createElement("a");
                a.href = link.url;
                a.textContent = link.label;
                linksWrap.appendChild(a);
            });
            bubble.appendChild(linksWrap);
        }
        messages.appendChild(bubble);
        messages.scrollTop = messages.scrollHeight;
    };

    const addTyping = () => {
        const typing = document.createElement("div");
        typing.className = "chatbot-typing";
        typing.id = "chatbot-typing";
        typing.textContent = "Thinking...";
        messages.appendChild(typing);
        messages.scrollTop = messages.scrollHeight;
    };

    const removeTyping = () => {
        const typing = document.getElementById("chatbot-typing");
        if (typing) typing.remove();
    };

    const toggleOpen = (open) => {
        root.classList.toggle("is-open", open);
        if (open) {
            launcher.setAttribute("aria-expanded", "true");
            input.focus();
        } else {
            launcher.setAttribute("aria-expanded", "false");
        }
    };

    launcher.addEventListener("click", () => toggleOpen(!root.classList.contains("is-open")));
    closeBtn.addEventListener("click", () => toggleOpen(false));

    addMessage("Hi! Tell me what you want to do and I will point you to the right page.", "assistant");

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const value = input.value.trim();
        if (!value) return;

        addMessage(value, "user");
        input.value = "";
        addTyping();

        try {
            const response = await fetch("/api/chat", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    message: value,
                    currentPath: window.location.pathname
                })
            });

            removeTyping();
            if (!response.ok) {
                addMessage("I had trouble reaching the assistant. Try again in a moment.", "assistant");
                return;
            }

            const data = await response.json();
            const answer = data.answer || "Here is what I found:";
            const links = Array.isArray(data.links) ? data.links : [];
            addMessage(answer, "assistant", links);
            if (data.followUp) {
                addMessage(data.followUp, "assistant");
            }
        } catch (err) {
            removeTyping();
            addMessage("I ran into a network issue. Please try again.", "assistant");
        }
    });
})();
