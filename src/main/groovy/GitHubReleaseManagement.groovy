import groovy.json.JsonOutput
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.GET
import static groovyx.net.http.Method.POST
import static groovyx.net.http.Method.DELETE

class GitHubReleaseManagement {

  private boolean debug = false
  // The action is internal and not exposed in the Usage
  private static String[] actions = ['debug', 'create', 'delete', 'latest', 'list']

  private String _owner  // Do not use 'owner', it's a keyword in Groovy
  private String token
  private String repo
  private String branch
  private String version
  private String assets
  private String notes

  private String release_name
  private String tag_name

  private def http_headers = [
      Accept      : 'application/vnd.github.v3+json',
      'User-Agent': 'TUI_GradleJenkins_Release'
  ]

  private String GHApiUrl = "https://api.github.com"


  static void main(String[] args) {
    def action

    if (!args ||
        !actions.contains("${args[0]}")
    ) {
      println "Invalid arguments."
      usage()
      return
    }

    def params = [] as Queue
    params.addAll(args)

    // Get parameter debug
    def debug = params.poll()

    action = params.poll()

    if (("${action}" == 'create' && args.length < 7 || args.length > 9) ||
        ("${action}" == 'delete' && args.length < 6) ||
        ("${action}" == 'latest' && args.length < 5) ||
        ("${action}" == 'list' && args.length < 5)
    ) {
      println "Invalid arguments."
      usage()
      return
    }

    def _owner = params.poll()
    def token = params.poll()
    def repo = params.poll()
    def version = ('create' == action || 'delete' == action) ? params.poll() : ""
    def branch = ('create' == action) ? params.poll() : ""
    def assets = ((!debug && 'create' == action && args.length > 6) || (debug && 'create' == action && args.length > 7)) ? params.poll() : ""
    def notes = ((!debug && 'create' == action && args.length > 7) || (debug && 'create' == action && args.length > 8)) ? params.poll() : ""

    println "- Parameters:"
    println "  - Action : $action"
    println "  - Owner  : $_owner"
    println "  - Token  : ***************"
    println "  - Repo   : $repo"
    println "  - Version: $version"
    println "  - Branch : $branch"
    println "  - Assets : $assets"
    println "  - Notes  : $notes"
    println "\n"

    GitHubReleaseManagement ghrm
    ghrm = new GitHubReleaseManagement(_owner, token, repo, version, branch, assets, notes)


    if ('create' == action) {
      println "- Create a Release"
      def resp = ghrm.getRelease(ghrm.tag_name)

      if(debug){
        println "  !!getRelease resp: $resp"
      }
      if (resp && resp.message) { // Version doesn't exist. Good, continue!
        if (debug)
          println "  - Version doesn't exist"
        resp = ghrm.createRelease()

        if(debug){
          println "  !!createRelease resp: $resp"
        }

        if (!resp || resp.message) {
          println "  - Error Release Creation failed: resp: $resp.message"
          return
        }
        println "  - Successful Release creation"
        if (!assets) {
          println "  - No asset to upload"
          return
        }

        println "  - Upload assets: '$assets'"
        if (debug)
          println "    - Upload URL '$resp.upload_url"
        def _assets = ghrm.uploadReleaseAsset(resp.upload_url)

        if(debug){
          println "  !!uploadReleaseAsset resp: $resp"
        }

        if (!_assets) {
          println "  - Uploading assets failed"
          return
        }
        int i = 0
        _assets.each { asset ->
          println "    - Asset #${++i}: "
          println "      - Path: $asset.path"
          println "      - Name: $asset.name"
          println "      - ContentType: $asset.contentType"

          if (asset.message) {
            println "      - Upload failed: $asset.message"
          } else {
            println "      - Upload was successful:"
            println "        - Download URL: ${asset.download_url}"
          }
        }
      } else {
        println "- Error: a release already exists for the version: $version"
      }
    } else if ('delete' == action) {
      println "- Delete a Release"

      def resp = ghrm.getRelease(ghrm.tag_name)

      if (!resp || resp.message) {
        println "  - Error getting release: resp: $resp.message"
      } else {

        if (debug)
          println "  - Release found. id : $resp.id"

        resp = ghrm.deleteRelease(resp.id)

        if (resp && resp.message) {
          println "  - Error deletting release: resp: $resp.message"
        } else {
          println "  - release: '$ghrm.release_name' deleted."
        }
      }

    } else if ('latest' == action) {
      println "- Get latest release."

      def resp = ghrm.getRelease('latest')

      if (!resp || resp.message) {
        println "  - Error getting release: resp: $resp.message"
      } else {

        int i = 0
        println "  - Latest release found:"
        println "    - name      : $resp.name"
        println "    - Author    : $resp.author.login"
        println "    - Published : $resp.published_at"
        println "    - Artifacts : "
        resp.assets.each {
          println "      - Artifacts #${++i}:"
          println "        - Name : ${it.name}"
//          println "        - content type: ${it.content_type}"
          println "        - Size : ${it.size} bytes"
          println "        - Download at : ${it.browser_download_url}"
        }
      }
    } else if ('list' == action) {
      def resp = ghrm.listReleases()

      if (!resp || resp.message) {
        println "Error: resp: $resp.message"
      } else {
//        println "Latest release url: $resp.url"
      }
    }
  }

  static void usage() {
    println "Usage:"
    println "GitHubReleaseManagement <action> <user> <token> <repo> <version> <branch> <assets> <notes>"

    println "action : (required) Available values: 'create', 'delete', 'latest', 'list'"
    println "         -'create': Allows to create a release and optionally upload artifacts (assets)"
    println "         -'delete': Delete an existent release. The 'version' number is required."
    println "         -'latest': Retrieve details about the latest release"
    println "         -'list'  : List all the available releases"
    println "owner  : (required) GiHub user or organisation"
    println "token  : (required) GiHub personal access token. See https://github.com/blog/1509-personal-api-tokens"
    println "repo   : (required) GiHub repository"
    println "version: (required for action='create' & 'delete') Release version"
    println "branch : (required for action='create') GiHub repository branch to release from. Ex'master'"
    println "assets : (Optional used with action='create') Assets to attach to the release."
    println "         - Format: <file1>,<file2>,..."
    println "           Comma delimited List of file path"
    println "notes  : (Optional used with action='create') Release notes associated to the Release"
    println "\nImportant:"
    println "----------"
    println "The naming convention for the release and the associated tag are based on the 'version' parameter with following format: "
    println " - Release name = v<version>"
    println " - Tag name     = tag-<version>"
  }


  GitHubReleaseManagement(String _owner, String token, String repo, String version = '', String branch = '', String assets = '', String notes = '') {

    this._owner = _owner
    this.token = token
    this.repo = repo
    this.version = version
    this.branch = branch
    this.assets = assets
    this.notes = notes

    this.release_name = "v${version}".toString()
    this.tag_name = "tag-v${version}".toString()

    http_headers << [
        Authorization: "token ${token}".toString(),
    ]
  }

  def listReleases() {

    def http = new HTTPBuilder(GHApiUrl)

    http.setHeaders(http_headers)

    http.request(GET, JSON) {
      uri.path = "/repos/$_owner/$repo/releases"

      if (GitHubReleaseManagement.debug)
        println "GET  ${GHApiUrl}/${uri.path}"

      response.failure = { resp, json ->
        if (GitHubReleaseManagement.debug) {
          println 'Request failed'
          println "Response status: ${resp.statusLine}"
          println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
          println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
        }

        return json
      }

      response.'422' = { resp, json ->
        if (GitHubReleaseManagement.debug) {
          println 'Request failed: invalid parameters'
          println "Response status: ${resp.statusLine}"
          println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
          println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
        }

        return json
      }

      response.success = { resp, json ->
        if (GitHubReleaseManagement.debug) {
          println 'Request was successful'
          println "Response status: ${resp.statusLine}"
          println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
          println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
        }

        return json
      }
    }
  }

  def getRelease(tag_or_latest) {

    def http = new HTTPBuilder(GHApiUrl)

    http.setHeaders(http_headers)

    http.request(GET, JSON) {
      if ('latest' == tag_or_latest)
        uri.path = "/repos/$_owner/$repo/releases/latest"
      else
        uri.path = "/repos/$_owner/$repo/releases/tags/$tag_or_latest"

      if (GitHubReleaseManagement.debug)
        println "GET  ${GHApiUrl}/${uri.path}"

      response.failure = { resp, json ->
        if (GitHubReleaseManagement.debug) {
          println 'Request failed'
          println "Response status: ${resp.statusLine}"
          println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
          println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
        }

        return json
      }

      response.'422' = { resp, json ->
        if (GitHubReleaseManagement.debug) {
          println 'Request failed: invalid parameters'
          println "Response status: ${resp.statusLine}"
          println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
          println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
        }

        return json
      }

      response.success = { resp, json ->
        if (GitHubReleaseManagement.debug) {
          println 'Request was successful'
          println "Response status: ${resp.statusLine}"
          println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
          println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
        }

        return json
      }
    }

  }

  def deleteRelease(id) {

    def http = new HTTPBuilder(GHApiUrl)

    http.setHeaders(http_headers)

    http.request(DELETE) {
      uri.path = "/repos/$_owner/$repo/releases/$id"

      if (GitHubReleaseManagement.debug)
        println "DELETE  ${GHApiUrl}/${uri.path}"

      response.failure = { resp, json ->
        if (GitHubReleaseManagement.debug) {
          println 'Request failed'
          println "Response status: ${resp.statusLine}"
          println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
          println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
        }

        return json
      }

      response.'422' = { resp, json ->
        if (GitHubReleaseManagement.debug) {
          println 'Request failed: invalid parameters'
          println "Response status: ${resp.statusLine}"
          println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
          println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
        }

        return json
      }

      response.'204' = { resp ->
        if (GitHubReleaseManagement.debug)
          println 'Release deleted successfully'

        return null
      }
    }

  }

  def createRelease() {
    // Branch name : remove characters not supported by the api.
    // If branch starts with 'origin/' remove this prefix

    branch = (!"$branch".startsWith('origin/')) ?: ("$branch" - 'origin/')
    def http = new HTTPBuilder(GHApiUrl)

    http.setHeaders(http_headers)

    http.request(POST, JSON) {
      uri.path = "/repos/$_owner/$repo/releases"

      body = [
          name            : release_name,
          tag_name        : tag_name,
          target_commitish: branch,
          body            : notes,
          draft           : false,
          prerelease      : false
      ]

      if (GitHubReleaseManagement.debug) {
        println "POST  ${GHApiUrl}/${uri.path}"
        println "Request body: $body"
      }

      response.failure = { resp, json ->
        if (GitHubReleaseManagement.debug) {
          println 'Request failed'
          println "Response status: ${resp.statusLine}"
          println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
          println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
        }

        return json
      }

      response.'422' = { resp, json ->
        if (GitHubReleaseManagement.debug) {
          println 'Request failed: invalid parameters'
          println "Response status: ${resp.statusLine}"
          println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
          println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
        }

        return json
      }

      response.success = { resp, json ->
        if (GitHubReleaseManagement.debug) {
          println 'Request was successful'
          println "Response status: ${resp.statusLine}"
          println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
          println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
        }

        return json
      }
    }
  }

  def uploadReleaseAsset(upload_url) {

    if (!assets)
      return null

    def _assets = []
    String[] assetsPath = assets.split(',')
    def map = URLConnection.getFileNameMap()

    int i = 0
    assetsPath.each {
      if (GitHubReleaseManagement.debug)
        println "Asset #${++i}: $it"
      def f = new File(it)
      if (f.exists()) {
        _assets << new UploadAsset(path: it, name: f.name, file: f, contentType: map.getContentTypeFor(f.absolutePath))
        if (GitHubReleaseManagement.debug)
          println " - Asset path '$it' found"
      } else {
        _assets << new UploadAsset(path: it, message: "Asset path '$it' not found")
        if (GitHubReleaseManagement.debug)
          println " - Asset path '$it' not found"
      }
    }

    if (GitHubReleaseManagement.debug)
      println "Total assets to upload $i"

//    println "Original Upload url: '$upload_url'"
    upload_url = "$upload_url".substring(0, "$upload_url".indexOf('{?'))

    if (GitHubReleaseManagement.debug)
      println "Upload url: '$upload_url'"

    def http = new HTTPBuilder(upload_url)
    http.ignoreSSLIssues()

    i = 0
    _assets.each { UploadAsset _asset ->
      if (GitHubReleaseManagement.debug) {
        println "Asset #${++i}:"
        println " - Path: $_asset.path"
        println " - Name: $_asset.name"
        println " - ContentType: $_asset.contentType"
      }

      def h = http_headers
      h << ['Content-Type': _asset.contentType]
      http.setHeaders(h)

      http.request(POST) {
        uri.query = [name: "$_asset.name"]

        send ContentType.BINARY, _asset.file.bytes

        if (GitHubReleaseManagement.debug)
          println "POST  ${upload_url}/${uri.path}"

        response.failure = { resp, json ->
          if (GitHubReleaseManagement.debug) {
            println 'Request failed'
            println "Response status: ${resp.statusLine}"
            println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
            println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
          }

          _asset.message = 'Request failed'
          _asset.json = json
          return _assets
        }

        response.'422' = { resp, json ->
          if (GitHubReleaseManagement.debug) {
            println 'Request failed: invalid parameters'
            println "Response status: ${resp.statusLine}"
            println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
            println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
          }

          _asset.message = 'Request failed: invalid parameters'
          _asset.json = json
          return _assets
        }

        response.'201' = { resp, json ->
          if (GitHubReleaseManagement.debug) {
            println 'Request was successful'
            println "Response status: ${resp.statusLine}"
            println 'Response headers: \n' + resp.headers.collect { "- $it" }.join('\n')
            println "Response Json : ${JsonOutput.prettyPrint(JsonOutput.toJson(json))}"
          }

          _asset.json = json
          _asset.download_url = json.browser_download_url
          return _assets
        }
      }
    }
  }

  private class UploadAsset {
    String path
    String name
    File file
    String contentType

    String message
    String download_url

    def json
  }

}

