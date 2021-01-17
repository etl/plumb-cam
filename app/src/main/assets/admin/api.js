if (location.hostname === 'localhost') {
  var host = 'http://localhost:8888/api';
  var imgHost = 'http://localhost:8888';
} else {
  var host = location.origin + '/api'
  var imgHost = location.origin;
}

api = {
  getStatus: function () {
    return request('status');
  },
  getConfig: function () {
    return request('getConfig');
  },
  setConfig(config) {
    return request('setConfig', config);
  },
  restart() {
    return request('restart');
  },
  shutdown() {
    return request('shutdown');
  },
  capture() {
    return request('capture').then((r) => {
      r.result = imgHost + '/' + r.result;
      return r;
    });
  },
};

async function request(cmd, params) {
  try {
    // let host = window.location.host;
    let url = new URL(host + '/' + cmd);
    if (params) url.search = new URLSearchParams(params).toString();

    let response = await fetch(url.toString());
    if (response.ok) {
      return await response.json();
    } else {
      throw response.status;
    }
  } catch (err) {
    console.log(`Error requesting '${cmd}' ${err}`);
    return { error: err };
  }
}
