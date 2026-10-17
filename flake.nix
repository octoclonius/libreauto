{
  inputs = {
    flake-parts = {
      url = "github:hercules-ci/flake-parts";
    };
    nixpkgs = {
      url = "github:nixos/nixpkgs/nixos-unstable";
    };
  };

  outputs =
    inputs@{
      flake-parts,
      ...
    }:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [
        "aarch64-darwin"
        "aarch64-linux"
        "x86_64-linux"
      ];

      perSystem =
        {
          system,
          ...
        }:
        let
          inherit (inputs.nixpkgs) lib;
          pkgs = import inputs.nixpkgs {
            inherit system;
            config = {
              allowUnfree = false;
            };
          };
        in
        {
          devShells = {
            default = pkgs.mkShell {
              packages = with pkgs; [
                openssl
                python3
              ];
            };
          };

          apps = {
            testcerts = {
              type = "app";
              program = lib.getExe (
                pkgs.writeShellApplication {
                  name = "testcerts";
                  runtimeInputs = with pkgs; [
                    openssl
                    python3
                  ];
                  runtimeEnv = {
                    TESTKEY = ./scripts/testkey.py;
                  };
                  text = builtins.readFile ./scripts/testcerts.sh;
                }
              );
            };
          };

          formatter = pkgs.nixfmt;
        };
    };
}
