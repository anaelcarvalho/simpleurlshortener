var ShortenerRow = React.createClass({
	getInitialState: function() {
		return {shortenerText: '', authToken: ''};
	},
	deleteShortener: function() {
		this.props.onShortenerDelete({shortenerText: this.state.shortenerText, authToken: this.state.authToken});
	},
	componentDidMount: function() {
		this.setState({shortenerText: this.props.shortenerText});
		this.setState({authToken: this.props.authToken});
	},
	render: function() {
		return (
			<tr>
				<td>{this.props.shortener.id}</td>
				<td><a href={"/"+this.props.shortener.shortener} target="_blank">{this.props.shortener.shortener}</a></td>
				<td>{this.props.shortener.url}</td>
				<td>{this.props.shortener.dateInserted}</td>
				<td>{this.props.shortener.hitCount}</td>
				<td><span onClick={this.deleteShortener}>Delete</span></td>
			</tr>
		);
	}
});

var ShortenerTable = React.createClass({
	handleShortenerDelete: function(shortenerData) {
		this.props.onShortenerDelete({shortenerText: shortenerData['shortenerText'], 
			authToken: shortenerData['authToken']});
	},
	render: function() {
		var rows = [];
		this.props.shorteners.forEach(function(shortener) {
			rows.push(
					<ShortenerRow 
						shortener={shortener} 
						key={shortener.id} 
						shortenerText={shortener.shortener} 
						authToken={shortener.authToken} 
						onShortenerDelete={this.handleShortenerDelete}
					/>);
		}.bind(this));
		return (
			<table border="0" cellspacing="5" cellpadding="5">
				<thead>
					<tr>
						<th>Id</th>
						<th>Shortener</th>
						<th>URL</th>
						<th>Date Added</th>
						<th>Hits</th>
						<th>Action</th>
					</tr>
				</thead>
				<tbody>{rows}</tbody>
			</table>
		);
	}
});

var AddShortenerBar = React.createClass({
	getInitialState: function() {
		return {url: ''};
	},
	handleUrlChange: function(e) {
		this.setState({url: e.target.value});
	},
	handleSubmit: function(e) {
		e.preventDefault();
		var url = this.state.url.trim();
		if (!url) {
			return;
		}
		this.props.onShortenerSubmit({url: url});
		this.setState({url: ''});
	},
	render: function() {
		return (
				<form onSubmit={this.handleSubmit}>
				<input 
					type="text" 
					placeholder="Enter URL" 
					value={this.state.url}
					onChange={this.handleUrlChange}
				/>
				<input type="submit" value="Add" />
				</form>
		);
	}
});

var SampleURLShortener = React.createClass({
	loadShortenersFromServer: function() {
		$.ajax({
			url: this.props.urlList,
			dataType: 'json',
			cache: false,
			success: function(shorteners, status, xhr) {
				console.log(xhr.getResponseHeader('Link'));
				this.setState({shorteners: shorteners});
			}.bind(this),
			error: function(xhr, status, err) {
				console.error(this.props.url, status, err.toString());
			}.bind(this)
		});
	},
	getInitialState: function() {
		return {shorteners: []};
	},
	componentDidMount: function() {
		this.loadShortenersFromServer();
	},
	handleShortenerSubmit: function(shortenerUrl) {
		$.ajax({
			url: this.props.urlCreate,
			type: 'POST',
			data: {
				url : shortenerUrl['url']
			},
			encode: true,
			success: function(data) {
				this.loadShortenersFromServer();
			}.bind(this),
			error: function(xhr, status, err) {
				console.error(this.props.url, status, err.toString());
			}.bind(this)
		});
	},
	handleShortenerDelete: function(shortenerData) {
		$.ajax({
			url: this.props.urlDelete.concat(shortenerData['shortenerText']),
			headers: {
				'Authorization': shortenerData['authToken']
			},
			type: 'DELETE',
			success: function(data) {
				this.loadShortenersFromServer();
			}.bind(this),
			error: function(xhr, status, err) {
				console.error(this.props.url, status, err.toString());
			}.bind(this)
		});	
	},
	render: function() {
		return (
				<div>
				<AddShortenerBar onShortenerSubmit={this.handleShortenerSubmit} /> <span onClick={this.loadShortenersFromServer}>Reload</span>
				<ShortenerTable shorteners={this.state.shorteners} onShortenerDelete={this.handleShortenerDelete} />
				</div>
		);
	}
});

ReactDOM.render(
			<SampleURLShortener 
				urlList="/list/0" 
				urlCreate="/create"
				urlDelete="/"
			/>,
			document.getElementById('container')
		);