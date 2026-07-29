document.querySelectorAll("[data-target]").forEach((button) => {
  button.addEventListener("click", () => {
    const target = document.getElementById(button.dataset.target);
    target?.scrollIntoView({ behavior: "smooth", block: "start" });
  });
});

document.querySelectorAll(".tab-bar button").forEach((button) => {
  button.addEventListener("click", () => {
    const label = button.textContent.trim();
    const targetMap = {
      "首页": "screen-home",
      "登记": "screen-form-one",
      "记录": "screen-records",
      "我的": "screen-profile"
    };
    const target = document.getElementById(targetMap[label]);
    target?.scrollIntoView({ behavior: "smooth", block: "start" });
  });
});

if (window.lucide) {
  window.lucide.createIcons({
    attrs: {
      "aria-hidden": "true",
      "focusable": "false"
    }
  });
}
