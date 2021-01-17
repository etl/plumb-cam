webix.ready(function () {
  ui = webix.ui(markup);
  setInterval(loadStatus, 5000);
  loadConfig();
});

let header = {
  // height: 40,
  view: 'toolbar',
  css: {
    background: '#dadee0',
  },
  cols: [
    { view: 'icon', icon: 'mdi mdi-camera-wireless' },
    { view: 'label', label: 'PlumbCam Control' },
    { id: 'idStatus', view: 'label', label: '', align: 'right' },
  ],
};

let sideBar = {
  padding: 5,
  margin: 5,
  width: 300,
  rows: [
    { view: 'button', label: 'Capture', css: 'webix_primary', click: capture },
    {
      id: 'idStatusEx',
      autoheight: true,
      nameWidth: 120,
      view: 'property',
      elements: [],
      editable: false,
      tooltip: true,
    },
    {},
    { label: 'Shutdown', view: 'button', css: 'webix_danger', click: shutdown },
    { label: 'Restart', view: 'button', css: 'webix_danger', click: restart },
    { height: 20 },
    { id: 'idConfig', autoheigth: true, view: 'property', elements: [], tooltip: true, nameWidth: 120 },
    {
      cols: [
        { label: 'Save', view: 'button', click: saveConfig },
        { label: 'Load', view: 'button', click: loadConfig },
      ],
    },
  ],
};

markup = {
  rows: [
    header,
    {
      cols: [
        sideBar,
        { view: 'resizer' },
        {
          rows: [
            {
              cols: [
                { id: 'idCaptureView', view: 'template', template: '', gravity:2},
                { view: 'resizer' },
                { id: 'idFiles', view: 'template', template: 'You can place any widget here..', gravity: 1 },
              ],
            },
            { view: 'resizer' },
            {
              rows: [
                {
                  template: 'Console',
                  view: 'template',
                  $css: { background: '#444' },
                  type: 'header',
                  autoheight: true,
                },
                { view: 'textarea', id: 'idLog', height: 200 },
              ],
            },
          ],
        },
      ],
    },
  ],
};

function loadStatus() {
  api.getStatus().then((response) => {
    if (response.error) {
      webix.message({ text: `loadStatus Error: ${response.error}`, type: 'error', expire: 5000 });
      ui.disable();
      return;
    } else {
      ui.enable();
    }
    let r = response.result;
    let html = `<code>ver: ${r.version} | <b>${r.status}</b></code>`;
    $$('idStatus').setValue(html);

    $$('idStatusEx').define({
      elements: Object.keys(r).map((key) => {
        val = r[key];
        if (key === 'uptime') val = (val / 1000).toFixed() + 's';
        if (key === 'lastCaptureTime') val = new Date(val);
        return { label: key, type: 'text', value: val };
      }),
    });
    $$('idStatusEx').refresh();
  });
}

function loadConfig() {
  api.getConfig().then((response) => {
    if (response.error) {
      webix.message({ text: `loadConfig Error: ${response.error}`, type: 'error', expire: 5000 });
      return;
    } else {
      webix.message({ text: 'loadConfig done', type: 'success', expire: 5000 });
    }
    let r = response.result;
    let idConfig = $$('idConfig');
    idConfig.define({
      elements: Object.keys(r).map((key) => {
        val = r[key];
        return { label: key, type: 'text', value: val, readonly: key === 'inited' };
      }),
    });
    idConfig.refresh();
    idConfig.attachEvent('onBeforeEditStart', function (cell) {
      return idConfig.getItem(cell).label !== 'inited';
    });
  });
}

function saveConfig() {
  let idConfig = $$('idConfig');
  let config = {};
  for (id of Object.keys(idConfig.getValues())) {
    let item = idConfig.getItem(id);
    if (item.readonly) continue;
    config[item.label] = item.value;
  }

  api.setConfig(config).then((response) => {
    if (response.error) {
      webix.message({ text: `setConfig Error: ${response.error}`, type: 'error', expire: 5000 });
    } else {
      webix.message({ text: 'setConfig Saved', type: 'success', expire: 5000 });
    }
  });
}

function shutdown() {
  api.shutdown().then((response) => {
    if (response.error) {
      webix.message({ text: `shutdown Error: ${response.error}`, type: 'error', expire: 5000 });
    } else {
      webix.message({ text: 'shutdown done', type: 'success', expire: 5000 });
    }
  });
}

function restart() {
  api.restart().then((response) => {
    if (response.error) {
      webix.message({ text: `restart Error: ${response.error}`, type: 'error', expire: 5000 });
    } else {
      webix.message({ text: 'restart sent', type: 'success', expire: 5000 });
    }
  });
}

function capture() {
  api.capture().then((response) => {
    if (response.error) {
      webix.message({ text: `capture Error: ${response.error}`, type: 'error', expire: 5000 });
    } else {
      $$('idCaptureView').setHTML(`<a target='_blank' href='${response.result}'><img src='${response.result}' ></a>`);
    }
  });
}
