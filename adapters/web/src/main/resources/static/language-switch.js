// Warns before a language switch would discard unsaved form input (FR-038). Constitution v1.2.0: static, no
// library, no business logic, no data; without JavaScript the switch still works, just without the warning.
(function () {
  function fieldChanged(field) {
    if (field.type === "checkbox" || field.type === "radio") return field.checked !== field.defaultChecked;
    if (field.tagName === "SELECT") {
      return Array.prototype.some.call(field.options, function (option) { return option.selected !== option.defaultSelected; });
    }
    if (field.type === "hidden" || field.type === "submit" || field.type === "button") return false;
    return field.value !== field.defaultValue;
  }

  function hasUnsavedInput(switchForm) {
    return Array.prototype.some.call(document.forms, function (form) {
      return form !== switchForm && Array.prototype.some.call(form.elements, fieldChanged);
    });
  }

  document.addEventListener("submit", function (event) {
    var form = event.target;
    if (!form.classList.contains("language-switch")) return;
    if (hasUnsavedInput(form) && !window.confirm(form.getAttribute("data-unsaved-warning"))) event.preventDefault();
  });
})();
