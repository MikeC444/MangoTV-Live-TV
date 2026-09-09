(function () {
  "use strict";

  var token = new URLSearchParams(window.location.search).get("token");

  var tabsEl = document.getElementById("tabs");
  var formEl = document.getElementById("form");
  var messageEl = document.getElementById("message");
  var submitButton = document.getElementById("submit");
  var displayNameField = document.getElementById("displayName");
  var passwordField = document.getElementById("password");
  var emailField = document.getElementById("email");
  var tabLogin = document.getElementById("tab-login");
  var tabRegister = document.getElementById("tab-register");
  var mode = "login";

  function setMessage(text, kind) {
    messageEl.textContent = text || "";
    messageEl.className = kind || "";
  }

  function hideForm() {
    formEl.style.display = "none";
    tabsEl.style.display = "none";
  }

  function setMode(next) {
    mode = next;
    tabLogin.classList.toggle("active", mode === "login");
    tabRegister.classList.toggle("active", mode === "register");
    displayNameField.style.display = mode === "register" ? "block" : "none";
    passwordField.autocomplete = mode === "register" ? "new-password" : "current-password";
    submitButton.textContent = mode === "register" ? "Create Account" : "Sign In";
    setMessage("");
  }

  tabLogin.addEventListener("click", function () {
    setMode("login");
  });
  tabRegister.addEventListener("click", function () {
    setMode("register");
  });

  if (!token) {
    hideForm();
    setMessage("This link is missing its activation code. Go back to your TV and scan the QR code again.", "error");
    return;
  }

  fetch("/auth/qr/resolve?token=" + encodeURIComponent(token))
    .then(function (response) {
      return response.json();
    })
    .then(function (data) {
      if (data.status === "expired" || data.status === "not_found") {
        hideForm();
        setMessage("This code has expired. Go back to your TV and try again.", "error");
      } else if (data.status === "consumed" || data.status === "completed") {
        hideForm();
        setMessage("This code has already been used. Check your TV — it may already be signed in.", "success");
      }
    })
    .catch(function () {
      // A failed pre-check doesn't block the form — submitting will
      // surface a clear error anyway, and a transient network hiccup here
      // shouldn't stop a legitimate attempt.
    });

  formEl.addEventListener("submit", function (event) {
    event.preventDefault();
    submitButton.disabled = true;
    setMessage("");

    var body = {
      token: token,
      mode: mode,
      email: emailField.value,
      password: passwordField.value,
    };
    if (mode === "register" && displayNameField.value.trim()) {
      body.displayName = displayNameField.value.trim();
    }

    fetch("/auth/qr/complete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    })
      .then(function (response) {
        if (response.status === 204) {
          hideForm();
          setMessage("You're signed in! Check your TV.", "success");
          return;
        }
        return response.json().then(function (data) {
          setMessage((data && data.error) || "Something went wrong. Please try again.", "error");
          submitButton.disabled = false;
        });
      })
      .catch(function () {
        setMessage("Couldn't reach the server. Check your connection and try again.", "error");
        submitButton.disabled = false;
      });
  });
})();
