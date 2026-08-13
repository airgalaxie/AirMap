
componentconstructors['chat'] = function(dynmap, configuration) {
	var me = this;
	
	if(dynmap.getBoolParameterByName("hidechat"))
		return;
		
	// Provides 'chat'-events by monitoring the world-updates.
	$(dynmap).bind('worldupdate', function(event, update) {
		swtch(update.type, {
			chat: function() {
				$(dynmap).trigger('chat', [{source: update.source, name: update.playerName, text: update.message, account: update.account,
                channel: update.channel}]);
			}
		});
	});

};
