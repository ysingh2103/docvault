(function () {
  var file = document.getElementById('file');
  var drop = document.getElementById('drop');
  var text = document.getElementById('dropText');
  var title = document.getElementById('title');
  var filter = document.getElementById('filter');
  var rows = document.querySelectorAll('#rows tr');
  var MAX = 20 * 1024 * 1024;
  var idle = text ? text.textContent : '';

  function fmt(n) {
    if (n < 1024) return n + ' B';
    if (n < 1048576) return (n / 1024).toFixed(1) + ' KB';
    return (n / 1048576).toFixed(1) + ' MB';
  }

  if (file && drop && text) {
    file.addEventListener('change', function () {
      var f = file.files[0];
      file.setCustomValidity('');
      drop.classList.remove('has');
      if (!f) { text.textContent = idle; return; }
      if (f.size > MAX) {
        file.setCustomValidity('Choose a file of 20 MB or less.');
        text.textContent = f.name + ' is ' + fmt(f.size) + ', over the 20 MB limit';
        return;
      }
      text.textContent = f.name + ' (' + fmt(f.size) + ')';
      drop.classList.add('has');
      if (title && !title.value) { title.value = f.name.replace(/\.[^.]+$/, ''); }
    });
    ['dragenter', 'dragover'].forEach(function (ev) {
      drop.addEventListener(ev, function () { drop.classList.add('over'); });
    });
    ['dragleave', 'drop'].forEach(function (ev) {
      drop.addEventListener(ev, function () { drop.classList.remove('over'); });
    });
  }

  if (filter) {
    filter.addEventListener('input', function () {
      var q = filter.value.trim().toLowerCase();
      rows.forEach(function (r) { r.hidden = q !== '' && r.getAttribute('data-q').indexOf(q) === -1; });
    });
  }
})();
